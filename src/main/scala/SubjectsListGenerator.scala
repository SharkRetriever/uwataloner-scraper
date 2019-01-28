import java.io.{InputStreamReader, InvalidObjectException}
import java.net.URL

import com.eclipsesource.json.{Json, JsonArray, JsonValue}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object SubjectsListGenerator {
  def generateSubjectsListCode(): String = {
    try {
      // We first grab the list of courses offered this term
      val termSubjects: mutable.Set[String] = mutable.Set()

      val termUrl: URL = new URL(s"https://api.uwaterloo.ca/v2/terms/list.json?key=${KeyHolder.Key}")
      val termJson: JsonValue = Json.parse(new InputStreamReader(termUrl.openStream()))
      var status: Int = termJson.asObject().get("meta").asObject().getInt("status", -1)
      if (status != 200) {
        throw new InvalidObjectException("Invalid status")
      }

      val termId: Int = termJson.asObject().get("data").asObject().getInt("current_term", -1)
      if (termId < 0) {
        throw new InvalidObjectException("Invalid term")
      }

      val coursesListUrl: URL = new URL(s"https://api.uwaterloo.ca/v2/terms/$termId/courses.json?key=${KeyHolder.Key}")
      val coursesListJson: JsonValue = Json.parse(new InputStreamReader(coursesListUrl.openStream()))
      status = coursesListJson.asObject().get("meta").asObject().getInt("status", -1)
      if (status != 200) {
        throw new InvalidObjectException("Invalid status")
      }

      val coursesArray: JsonArray = coursesListJson.asObject().get("data").asArray()

      for (i <- 0 until coursesArray.size()) {
        val currentCourse: JsonValue = coursesArray.get(i)
        val subjectName: String = currentCourse.asObject().getString("subject", "")
        if (subjectName.isEmpty) {
          throw new InvalidObjectException("Invalid subject name detected")
        }
        termSubjects += subjectName
      }

      // now we have the distinct list of subjects offered this term, so start grabbing descriptions
      val subjectsList: ListBuffer[(String, String)] = new ListBuffer[(String, String)]
      val subjectsListUrl: URL = new URL(s"https://api.uwaterloo.ca/v2/codes/subjects.json?key=${KeyHolder.Key}")
      val subjectsListJson: JsonValue = Json.parse(new InputStreamReader(subjectsListUrl.openStream()))
      status = subjectsListJson.asObject().get("meta").asObject().getInt("status", -1)
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
          if (termSubjects.contains(subjectName) && !subjectDescription.contains("(WLU)")) {
            subjectsList += ((subjectName, subjectDescription))
          }
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