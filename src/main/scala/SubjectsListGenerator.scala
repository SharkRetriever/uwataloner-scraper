import java.io.{InputStreamReader, InvalidObjectException}
import java.net.URL

import com.eclipsesource.json.{Json, JsonArray, JsonValue}

import scala.collection.mutable.ListBuffer

object SubjectsListGenerator {
  def generateSubjectsListCode(): String = {
    try {
      val subjectsList: ListBuffer[(String, String)] = new ListBuffer[(String, String)]

      val subjectsListUrl: URL = new URL(s"https://api.uwaterloo.ca/v2/codes/subjects.json?key=${KeyHolder.Key}")
      val subjectsListJson: JsonValue = Json.parse(new InputStreamReader(subjectsListUrl.openStream()))
      val status: Int = subjectsListJson.asObject().get("meta").asObject().getInt("status", -1)
      if (status != 200) {
        throw new InvalidObjectException("Invalid status")
      }
      else {
        val subjectsArray: JsonArray = subjectsListJson.asObject().get("data").asArray()
        for (i <- 0 until subjectsArray.size()) {
          val currentSubject: JsonValue = subjectsArray.get(i)
          val subjectName: String = currentSubject.asObject().getString("subject", "")
          val subjectDescription: String = currentSubject.asObject().getString("description", "")
          if (subjectName.isEmpty || subjectDescription.isEmpty) {
            throw new InvalidObjectException("Invalid subject name-description pair detected")
          }
          subjectsList += ((subjectName, subjectDescription))
        }

        val header = "fun getSubjectsList(): List<Subject> {\r\n\treturn listOf("
        val mainList = subjectsList.foldLeft("")((acc, cur) => String.format("%s\r\n\t\tSubject(\"%s\", \"%s\"),", acc, cur._1, cur._2)).stripSuffix(",")
        val ending = "\r\n\t)\r\n}\r\n"
        header + mainList + ending
      }
    }
    catch {
      case e: Exception => "Error in parse script."
    }
  }
}