# UWatALoner-Scraper #

## About ##

This scraper retrieves the following two types of information from the [UWaterloo API](https://uwaterloo.ca/api/):

- A list of all subjects along with their descriptions, e.g. MATH &ndash; Mathematics. Note that specific courses such as MATH 135 are not retrieved.
- A list of all courses, but only to generate a hashmap that maps buildings, e.g. MC, to list of rooms, e.g. \[1085, 4020, etc.\], where for each room, at least one section of one course uses that room.

The first list is generated into a Kotlin List<Subject>, where subject is of the form class Subject(val name: String, val description: String).
The form of the second hashmap is TBA.


