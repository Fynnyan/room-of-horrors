package com.fynnian.application.room

import com.benasher44.uuid.Uuid
import com.fynnian.application.APIException
import com.fynnian.application.common.Repository
import com.fynnian.application.common.room.Answer
import com.fynnian.application.common.room.Coordinates
import com.fynnian.application.config.DataSource
import com.fynnian.application.jooq.tables.records.AnswersRecord
import com.fynnian.application.jooq.tables.references.ANSWERS
import java.util.*

class AnswersRepository(dataSource: DataSource) : Repository(dataSource) {

  fun getAnswersOfRoom(roomCode: String): Map<UUID, List<Answer>> {
    return jooq {
      select(ANSWERS.asterisk())
        .from(ANSWERS)
        .where(ANSWERS.ROOM_CODE.eq(roomCode))
        .orderBy(ANSWERS.USER_ID, ANSWERS.ANSWER_NUMBER)
        .fetchGroups(ANSWERS.USER_ID)
        .map  { (userId, records) -> userId!! to records.map { it.into(ANSWERS).toDomain() } }
        .toMap()
    }
  }

  fun getAnswersOfUserForRoom(roomCode: String, userId: Uuid): List<Answer> {
    return jooq {
      select(ANSWERS.asterisk())
        .from(ANSWERS)
        .where(ANSWERS.ROOM_CODE.eq(roomCode))
        .and(ANSWERS.USER_ID.eq(userId))
        .orderBy(ANSWERS.ANSWER_NUMBER)
        .map { it.into(ANSWERS).toDomain() }
    }
  }

  fun getAnswerById(id: Uuid) {
    return jooq {
      select(ANSWERS.asterisk())
        .from(ANSWERS)
        .where(ANSWERS.ID.eq(id))
        .map { it.into(ANSWERS).toDomain() }
        .firstOrNull()
        ?: throw APIException.NotFound("Answer with id $id not found")
    }
  }

  fun upsertAnswer(answer: Answer): Answer {
    return jooq {
      insertInto(ANSWERS)
        .set(answer.toRecord())
        .onConflict(ANSWERS.ID)
        .doUpdate()
        .set(answer
          .toRecord()
          .also { it.updatedAt = nowAtCHOffsetDateTime() }
        )
        .returning()
        .map { it.into(ANSWERS).toDomain() }
        .first()
    }
  }

  fun deleteAnswer(answerId: Uuid, roomCode: String) {
    jooq {
      delete(ANSWERS)
        .where(ANSWERS.ID.eq(answerId).and(ANSWERS.ROOM_CODE.eq(roomCode)))
        .returning()
        .firstOrNull()
        ?: throw APIException.NotFound("Answer for this room $roomCode and id $answerId not found")
    }
  }
}

fun AnswersRecord.toDomain() = Answer(
  id = id!!,
  roomCode = roomCode!!,
  imageId = roomImageId!!,
  userId = userId!!,
  no = answerNumber!!,
  answer = answer!!,
  coordinates = Coordinates(
    horizontal = xCoordinate!!,
    vertical = yCoordinate!!
  )
)

fun Answer.toRecord() = AnswersRecord().also {
  it.id = id
  it.roomCode = roomCode
  it.roomImageId = imageId
  it.userId = userId
  it.answerNumber = no
  it.answer = answer
  it.xCoordinate = coordinates.horizontal
  it.yCoordinate = coordinates.vertical
}