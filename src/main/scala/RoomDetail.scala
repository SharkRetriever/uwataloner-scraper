class RoomDetail(val roomCode: String, val courseSubject: String, val courseNumber: String) {
  override def toString: String = "\"%s\"".format(roomCode)
}
