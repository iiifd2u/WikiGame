
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WikiGame(val urlBase:String, val urlParams:MutableMap<String, String>){

    //Минимальная часть задания - получить все ссылки со страницы по URL

    private var globalFlag = true // флаг для всех вложенных рекурсивных функций
    private val findedPath = mutableListOf<String>() //найденный путь
    private val allTitles = mutableSetOf<String>() // Пространство имён всех заголовков

    fun parseResponse(urlWithParams:URL):List<String>{
        /** Получает url, возвращает список заголовков*/
        val jsonResponse = Json.parseToJsonElement(urlWithParams.readText())
        val pages = jsonResponse.jsonObject["query"]!!.jsonObject["pages"]
        val countPagesKey = pages!!.jsonObject.keys.toList()[0]
        if (countPagesKey=="-1") return listOf() // пустой ответ
        val links = pages.jsonObject[countPagesKey]!!.jsonObject["links"]!!.jsonArray.toList()
        val titles = links.map { it.jsonObject["title"].toString() }.toList()
        return titles
    }

     fun createFullURL(start:String):URL{
        /** из заголовка делает полный url*/
        urlParams["titles"] = URLEncoder.encode(start, StandardCharsets.UTF_8.toString())
        val urlWithParams = "$urlBase?" + urlParams
         .mapValues { it.value.replace(oldChar = ' ', newChar = '_') }
         .map { pair -> pair.key + '=' + pair.value }
         .joinToString(separator = "&")
        return URL(urlWithParams)
     }

    //Дополнительная часть задания - найти самы короткий путь от одной статьи к другой

    private fun recursiveSearchPath(start:MutableList<String>, end:String, maxLength:Int = 7) {

        /**
         * Рекурсивная функция поиска пути между двумя статьями на Википедии
         * На вход принимает текущий путь до заголовка, [стартовый, промежуточный, промежуточный...],
         * Получает список всех заголовков, которые есть по последнему заголовку
         * Если в это списке есть нужный заголовок - прерывается и печатает полный путь до него, [стартовый, промежуточный, промежуточный, ..., конечный]
         * Если нет - для каждого заголовка вызывает сама себя с аргументом [стартовый, промежуточный, промежуточный, ..., текущий]
         * Вначале проверяет, если длина пути больше указанного, то прерывается
         * Добавляет только те заголовки, которых нет в allTitles
         */
        if (!globalFlag){
            return
        }
        if (start.count() >= maxLength) { //если достигли максимальной длины пути
            return
        }
        val urlWithParams = createFullURL(start[start.count() - 1]) //ссылка на последний элемент в пути
        val listTitles = parseResponse(urlWithParams)//Список ссылок на странице по такому url
        if (listTitles.map { it.replace("\"", "")  }.contains(end)){ //если заголовок найден - печатаем путь и выходим
            (start+end).forEach{
                findedPath.add(it)
            }
            globalFlag = false
            return
        }
        if (!globalFlag){
            return
        }

        listTitles
            .map { it.replace("\"", "") } //форматируем заголовки
            .filter { !allTitles.contains(it) } // фильтруем их по глобальному множеству заголовков
            .forEach{ // тут содержаться заголовки, ранее не использованные и точно являющиеся не-конечными
                if (globalFlag && (start.count()<maxLength)) {
                    val tempList = (start+it).toMutableList() //новый промежуточный массив
                    allTitles.add(it)
                    recursiveSearchPath(tempList, end, maxLength)
//                    println(tempList) // промежуточные пути
                }
            }
        return
    }
    private fun getRandomPath(start:MutableList<String>, end:String, maxLength:Int):List<String> {
        val allTitlesInPath = mutableSetOf<String>()
        val linksForCurrentTitleList = mutableListOf(start.last()) //помещаем сюда первый заголовок

        //пока длина списка не превысит границу либо пока элемент списка не совпадет с искомым
        while(linksForCurrentTitleList.last()!=end && linksForCurrentTitleList.count() < maxLength){
            val urlFull = createFullURL(linksForCurrentTitleList.last())
            try {
                val randomNextTitle:String = parseResponse(urlFull)
                    .map { it.replace("\"", "") }
                    .filter { !allTitlesInPath.contains(it) }
                    .random()
                allTitlesInPath.add(randomNextTitle)
                linksForCurrentTitleList.add(randomNextTitle)
            }catch (e:Exception) {
                break
            }
        }
        return linksForCurrentTitleList
    }

    private fun searchPathShortest(start:MutableList<String>, end:String, maxLength:Int, maxSteps:Int):List<String>? {

        /**Нерекурсивная функция, стоит рандомные пути и ищет самый короткий из них*/
        val listOfPaths = mutableListOf<List<String>>()

        for (iter in 0 until maxSteps){

            val newPath = getRandomPath(start, end, maxLength)
//            println("Path = $newPath")
            if (newPath.isNotEmpty() && newPath.last() == end){
                listOfPaths.add(newPath)
            }
            if (newPath.count() == 2 && newPath.last() == end){//если длина пути = 2, то более короткого пути нет
                return newPath
            }
        }
        if (listOfPaths.isNotEmpty()){
            return listOfPaths.minBy { it.count() }
        }
        return null
    }
    private fun searchPathShortestBuffer(start:MutableList<String>, end:String, maxLength:Int, maxSteps:Int):List<String>? {
        // Функции поиска, ищет по всем заголовкам
        // maxLength = максимальная степень вложенности
        // maxSteps = максимальное количество итераций


        if (start.last() == end) return start //eсли путь длины 1

        val allTitlesInPath = mutableSetOf<String>() // чтобы не добавлять одинаковых заголовков
        allTitlesInPath.add(start.last())
        val bigBuffer = mutableListOf<List<String>>() //буфер всех путей

        //если 2
        try {
            parseResponse(createFullURL(start.last())).shuffled().forEach{//для всех ссылок со стартовой страницы
                val formatted = it.replace("\"", "")
                if (formatted==end){
                    return listOf(start.last(), formatted) //если окажется что тут, тогда вернёт путь длины 2
                }
                if (!allTitlesInPath.contains(formatted)){
                    bigBuffer.add(listOf(start.last(),formatted))
                    allTitlesInPath.add(formatted)
                }
//                println("Last record in bigBuff_2=${bigBuffer.last()}")

            } //пути к текущему
        } catch (e:Exception){
            println("Некорректный запрос ${start.last()}")
        }

        // Если больше 2:

        for (i in 3 .. maxSteps){

            val currentRecordsInBuffer = bigBuffer.count() //сколько сейчас путей длины i-1
            for (step in 0..<currentRecordsInBuffer){

                val record = bigBuffer.removeAt(0) //по очереди убираем все элементы длины i-1
                try {
                    parseResponse(createFullURL(record.last())).shuffled().forEach{
                        if (!globalFlag) return null //когда одна корутина нашла, другие останавливают поиск
                        val formatted = it.replace("\"", "")
                        if (formatted==end){
                            globalFlag = false
                            return record+listOf(formatted) //если окажется что тут, тогда вернёт путь длины i
                        }
                        if (!allTitlesInPath.contains(formatted)){
                            bigBuffer.add(record+ listOf(formatted))
                            allTitlesInPath.add(formatted)
                        } //иначе добавляет в конец пути i
//                        println("Last record in bigBuff_$i = ${bigBuffer.last()}")
                    } //пути к текущему
                }catch (e:Exception){
                    println("Некорректный запрос ${createFullURL(record.last())}")
                }
            }
        }

        return null
    }

    fun playPathShortest(startTitle:String, end:String, maxSteps:Int = 30, maxLength: Int = 7):List<String>?{
        /**Сыграть в игру WikiGame
         * Путь возвращается в виде массива заголовков*/
        val startPathList = mutableListOf(startTitle)
        return searchPathShortestBuffer(startPathList, end, maxSteps=maxSteps, maxLength=maxLength)
    }

    //Синхронная функция
     fun play(startTitle:String, end:String, maxLength:Int = 7):List<String>{
        /**Сыграть в игру WikiGame
         * Путь возвращается в виде массива заголовков*/
         val startPathList = mutableListOf(startTitle)
         recursiveSearchPath(startPathList, end, maxLength)
         return findedPath
    }

}


suspend fun main() {

    val urlBase = "https://en.wikipedia.org/w/api.php"
    val urlParams = mutableMapOf(
        "action" to "query",
        "format" to "json",
        "prop" to "links",  // можно добавить |extlinks для внешних (не вики) ссылок
        "pllimit" to "max", // получать всё, без ограничений
        "plnamespace" to "0", //Выбирать только из основного пространства имён (собственно, статьи)
        )

    print("Стартовая статья: ")
    val startTitle  = readln() // Egypt
    print("Финишная статья: ")
    val endTitle =  readln()// The Lord of the Rings
    val wiki = WikiGame(urlBase, urlParams)

    println("#1 Все заголовки со страницы $startTitle:")
    val responseFromWikiTitles = wiki.parseResponse(wiki.createFullURL(startTitle))
    for (title in responseFromWikiTitles){
        println(title)
    }

//    //Функция №1
//    println("Рекурсивная функция поиска любого пути:")
//    println("Путь от $startTitle до $endTitle: "+wiki.play(startTitle, endTitle, maxLength=7))

    //Функция №2
    println("2# Функция поиска кратчайшего пути:")
    coroutineScope {  (1..7).map{
            println("Запуск подпроцесса $it ...")
            async(Dispatchers.Default) {
                wiki.playPathShortest(startTitle, endTitle, maxSteps=100, maxLength=3)
            }
        }.forEach{
            val res =it.await()
            if (res != null){
                println("Путь от $startTitle до $endTitle: ")
                println(res)
            }
        }
    }
}