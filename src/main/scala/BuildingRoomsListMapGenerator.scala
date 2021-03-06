import java.io.{InputStreamReader, InvalidObjectException}
import java.net.URL

import com.eclipsesource.json.{Json, JsonArray, JsonObject, JsonValue}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object BuildingRoomsListMapGenerator {
  def generateBuildingsRoomListMapCode(): String = {
    try {
      // We first grab the list of courses, and then start to fill in a hashmap structure
      val subjectsList: ListBuffer[(String, String)] = new ListBuffer[(String, String)]
      val buildingRoomsListMap: mutable.HashMap[String, ListBuffer[RoomDetail]] = new mutable.HashMap[String, ListBuffer[RoomDetail]]()

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
        val subjectCatalogNumber: String = currentCourse.asObject().getString("catalog_number", "")
        if (subjectName.isEmpty || subjectCatalogNumber.isEmpty) {
          throw new InvalidObjectException("Invalid subject name-catalog number pair detected")
        }
        if (// subjectName.charAt(0) <= 'C' &&
          !subjectCatalogNumber.endsWith("L") && subjectCatalogNumber.matches("[A-Za-z0-9]+") && subjectCatalogNumber.charAt(0) <= '3')
          subjectsList += ((subjectName, subjectCatalogNumber))
      }

      subjectsList.foreach((pair: (String, String)) => {
        // GET /courses/{subject}/{catalog_number}/schedule.{format}
        // this part is incredibly slow because we need the schedule for thousands of courses, aka thousands of API accesses
        val subject: String = pair._1
        val catalogNumber: String = pair._2
        val scheduleListUrl: URL = new URL(s"https://api.uwaterloo.ca/v2/courses/$subject/$catalogNumber/schedule.json?key=${KeyHolder.Key}")
        // FOR DEBUGGING PURPOSES
        println(s"Searching sections for $subject $catalogNumber")
        val scheduleListJson: JsonValue = Json.parse(new InputStreamReader(scheduleListUrl.openStream()))
        status = scheduleListJson.asObject().get("meta").asObject().getInt("status", -1)

        if (status != 200)
          throw new IllegalArgumentException("Invalid status")

        val sectionsList: JsonArray = scheduleListJson.asObject().get("data").asArray()
        for (i <- 0 until sectionsList.size()) {
          val section: String = sectionsList.get(i).asObject().getString("section", "")
          if (!(section.isEmpty || section.startsWith("SEM"))) {
            val classesList: JsonArray = sectionsList.get(i).asObject().get("classes").asArray()
            for (j <- 0 until classesList.size()) {
              val building: JsonObject = classesList.get(j).asObject().get("location").asObject()
              val buildingName: String = if (building.get("building").isNull) "" else building.getString("building", "")
              val buildingRoom: String = if (building.get("room").isNull) "" else building.getString("room", "")
              if (!(buildingName.isEmpty || buildingRoom.isEmpty)) {
                if (!buildingRoomsListMap.contains(buildingName)) {
                  buildingRoomsListMap.put(buildingName, ListBuffer[RoomDetail]())
                }
                buildingRoomsListMap(buildingName) += new RoomDetail(buildingRoom, subject, catalogNumber)
              }
            }
          }
        }
      })

      val header = "fun getBuildingRoomsListMap(): HashMap<String, List<String>> {\r\n\treturn hashMapOf(\r\n"
      // buildingsListRoomListMap must be appended to a StringBuffer
      // e.g. one element: \t\t"MC" to listOf("1085", "4042", ...),\r\n
      val sb: StringBuffer = new StringBuffer()
      buildingRoomsListMap.toSeq.sortBy(_._1).foreach((pair: (String, ListBuffer[RoomDetail])) => {
        // we want the actual building full name
        val buildingShortName: String = pair._1
        val buildingFullNameURL: URL = new URL(s"https://api.uwaterloo.ca/v2/buildings/$buildingShortName.json?key=${KeyHolder.Key}")
        val buildingFullNameJson: JsonValue = Json.parse(new InputStreamReader(buildingFullNameURL.openStream()))
        status = buildingFullNameJson.asObject().get("meta").asObject().getInt("status", -1)
        val buildingNameString: String = if (status == 200) {
          val buildingFullName: String = buildingFullNameJson.asObject().get("data").asObject().getString("building_name", "")
          buildingShortName + (if (buildingFullName.isEmpty) "" else " – " + buildingFullName)
        }
        else {
          buildingShortName + " – " + "N/A"
        }

        sb.append(String.format("\t\t\"%s\" to listOf(%s),\r\n".format(buildingNameString,
          pair._2.toArray.sortBy((x: RoomDetail) => x.roomCode).map(_.toString).distinct.mkString(", "))))
      })
      val ending = "\r\n\t)\r\n}\r\n"

      header + sb.toString.stripSuffix(",\r\n") + ending
    }
    catch {
      case e: Exception => {
        e.printStackTrace() // DEBUG
        "Error in parse script. " + e.toString
      }
    }
  }
}

