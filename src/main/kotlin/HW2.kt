
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URL


class WikiGame(val urlBase:String, val urlParams:MutableMap<String, String>){

    private var  globalFlag = true // флаг для всех вложенных рекурсивных функций
    private val findedPath = mutableListOf<String>() //найденный путь
    private val allTitles = mutableSetOf<String>() // Пространство имён всех заголовков

    private fun parseResponse(urlWithParams:URL):List<String>{
        /** Получает url, возвращает список заголовков*/
        val jsonResponse = Json.parseToJsonElement(urlWithParams.readText())
        val pages = jsonResponse.jsonObject["query"]!!.jsonObject["pages"]
        val countPagesKey = pages!!.jsonObject.keys.toList()[0]
        if (countPagesKey=="-1") return listOf() // пустой ответ
        val links = pages.jsonObject[countPagesKey]!!.jsonObject["links"]!!.jsonArray.toList()
        val titles = links.map { it.jsonObject["title"].toString() }.toList()
        return titles
    }

    private fun createFullURL(start:String):URL{
        /** из заголовка делает полный url*/
        urlParams["titles"] = start
        val urlWithParams = URL(urlBase + "?" + urlParams
            .mapValues { it.value.replace(oldChar = ' ', newChar = '_') }
            .map { pair -> pair.key + '=' + pair.value }
            .joinToString(separator = "&"))
        return urlWithParams
    }

    fun recursiveSearchPath(start:MutableList<String> , end:String, maxLength:Int = 7) {

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

        //пока длина списка не превысит границу либо пока ээлемент списка не совпадет с искомым
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
            if (newPath.isNotEmpty() && newPath.last() == end){
                listOfPaths.add(newPath)
            }
            if (newPath.count() == 2 && newPath.last() == end){//если длина пути = 2, то более короткого пути нет
                return newPath
            }
        }
            return try {
                listOfPaths.minBy { it.count() }
            }catch (e:Exception){
                null
            }
    }

    fun playPathShortest(startTitle:String, end:String, maxSteps:Int = 30, maxLength: Int = 7):List<String>?{
        /**Сыграть в игру WikiGame
         * Путь возвращается в виде массива заголовков*/
        val startPathList = mutableListOf(startTitle)
        return searchPathShortest(startPathList, end, maxSteps=maxSteps, maxLength=maxLength)
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

    val startTitle = "Egypt"
    val endTitle = "The Lord of the Rings"
    val endTitle2 = "Egyptian people"
    val wiki = WikiGame(urlBase, urlParams)

//    //Функция №1
//    println("Рекурсивная функция поиска любого пути:")
//    println("Путь от $startTitle до $endTitle: "+wiki.play(startTitle, endTitle, maxLength=7))

    //Функция №2
    println("Функция поиска кратчайшего пути:")
    coroutineScope {  (1..50).map{
            println("Запуск подпроцесса $it ...")
            async(Dispatchers.Default) {
                wiki.playPathShortest(startTitle, endTitle2, maxSteps=100, maxLength=4)
            }
        }.forEach{
            println("Путь от $startTitle до $endTitle2: ")
            println(it.await())
        }
    }
}