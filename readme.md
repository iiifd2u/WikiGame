## WikiGame

*Игра заключается в поиске (максимально короткого?) пути между
двумя заданными статьями на Википедии через ссылки на другие статьи на текущей странице*

2 функции: 

* Рекурсивная функция поиска пути между двумя статьями на Википедии
  * На вход принимает текущий путь до заголовка, [стартовый, промежуточный, промежуточный...],
  * Получает список всех заголовков, которые есть по последнему заголовку
  * Если в это списке есть нужный заголовок - прерывается и печатает полный путь до него, [стартовый, промежуточный, промежуточный, ..., конечный]
  * Если нет - для каждого заголовка вызывает сама себя с аргументом [стартовый, промежуточный, промежуточный, ..., текущий]
  * Вначале проверяет, если длина пути больше указанного, то прерывается
  * Добавляет только те заголовки, которых нет в allTitles
  
* Нерекурсивная функция, стоит рандомные пути и ищет самый короткий из них

**Для использования раскомментировать в майне ту или иную группу строк**