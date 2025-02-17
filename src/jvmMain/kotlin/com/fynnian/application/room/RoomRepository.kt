package com.fynnian.application.room

import com.benasher44.uuid.Uuid
import com.fynnian.application.APIException
import com.fynnian.application.common.Repository
import com.fynnian.application.common.room.*
import com.fynnian.application.config.DataSource
import com.fynnian.application.jooq.enums.RoomStatus
import com.fynnian.application.jooq.tables.records.RoomImagesRecord
import com.fynnian.application.jooq.tables.records.RoomsRecord
import com.fynnian.application.jooq.tables.references.*
import org.jooq.impl.DSL.*
import java.math.BigDecimal
import java.util.stream.Collectors.*
import com.fynnian.application.common.room.RoomStatus as RoomStatusDomain
import com.fynnian.application.jooq.enums.RoomStatus as RoomStatusJooq

class RoomRepository(dataSource: DataSource) : Repository(dataSource) {

  fun getRooms(code: String? = null): List<Room> {
    return jooq {
      select(ROOMS.asterisk(), ROOM_IMAGES.asterisk())
        .from(ROOMS)
        .leftJoin(ROOM_IMAGES).on(ROOM_IMAGES.ROOM_CODE.eq(ROOMS.CODE))
        .let {
          if (code != null) it.where(ROOMS.CODE.eq(code))
          else it
        }
        .fetchGroups(ROOMS, ROOM_IMAGES)
        .map { (room, images) ->
          room.toDomain().copy(images = images.filterNot { it.id == null }.map { it.toDomain() })
        }
    }
  }

  fun getRoom(code: String): Room {
    return getRooms(code).firstOrNull() ?: throw APIException.RoomNotFound(code)
  }

  fun getRoomImages(code: String): List<RoomImage> {
    return jooq {
      selectFrom(ROOM_IMAGES)
        .where(ROOM_IMAGES.ROOM_CODE.eq(code))
        .fetch { it.toDomain() }
    }
  }

  fun getRoomsForManagement(
    code: String? = null,
    status: RoomStatusDomain? = null
  ): List<RoomManagementDetail> {
    return jooq {

      // when no group info is collected, ROOM_GROUP_INFORMATION is null, use coalesce with 1 for the sum
      val participants = select(sum(coalesce(ROOM_GROUP_INFORMATION.GROUP_SIZE, 1)))
        .from(USERS_ROOM_STATUS)
        .leftJoin(ROOM_GROUP_INFORMATION).on(ROOM_GROUP_INFORMATION.USER_ID.eq(USERS_ROOM_STATUS.USER_ID))
        .where(USERS_ROOM_STATUS.ROOM_CODE.eq(ROOMS.CODE))
        .asField<BigDecimal>("participants")
      val groupCount = countDistinct(ROOM_GROUP_INFORMATION.USER_ID).`as`("group_count")
      val answers = countDistinct(ANSWERS.ID).`as`("answers")
      val participantsWithoutAnswers = selectCount()
          .from(USERS_ROOM_STATUS)
          .leftJoin(ANSWERS).on(ANSWERS.USER_ID.eq(USERS_ROOM_STATUS.USER_ID))
          .where(USERS_ROOM_STATUS.ROOM_CODE.eq(ROOMS.CODE))
          .and(ANSWERS.ID.isNull)
          .asField<Int>("participants_without_answers")

      select(
        ROOMS.asterisk(),
        ROOM_IMAGES.asterisk(),
        participants,
        groupCount,
        answers,
        participantsWithoutAnswers
      )
        .from(ROOMS)
        .leftJoin(ROOM_IMAGES).on(ROOM_IMAGES.ROOM_CODE.eq(ROOMS.CODE))
        .leftJoin(ROOM_GROUP_INFORMATION).on(ROOM_GROUP_INFORMATION.ROOM_CODE.eq(ROOMS.CODE))
        .leftJoin(USERS_ROOM_STATUS).on(USERS_ROOM_STATUS.ROOM_CODE.eq(ROOMS.CODE))
        .leftJoin(ANSWERS).on(ANSWERS.ROOM_CODE.eq(ROOMS.CODE))
        .where(trueCondition())
        .let { if (code != null) it.and(ROOMS.CODE.eq(code)) else it }
        .let { if (status != null) it.and(ROOMS.STATUS.eq(status.toRecord())) else it }
        .groupBy(ROOMS.CODE, ROOM_IMAGES.ID)
        .collect(
          groupingBy(
            {
              it.into(ROOMS).toRoomManagementDetail(
                participants = it.get(participants)?.toInt() ?: 0,
                participantsWithoutAnswers = it.get(participantsWithoutAnswers),
                answers = it.get(answers),
                groups = it.get(groupCount)
              )
            },
            filtering(
              { it.get(ROOM_IMAGES.ID) != null },
              mapping({ r -> r.into(ROOM_IMAGES).toDomain() }, toList())
            )
          )
        )
        .map { (room, images) -> room.copy(images = images) }
    }
  }

  fun getRoomForManagement(code: String): RoomManagementDetail {
    return getRoomsForManagement(code).firstOrNull() ?: throw APIException.RoomNotFound(code)
  }

  fun createRoom(roomCreation: RoomCreation): RoomManagementDetail {
    return jooq {
      selectFrom(ROOMS)
        .where(ROOMS.CODE.eq(roomCreation.code))
        .fetchOne()
        ?.also { throw APIException.BadRequest("Room with code ${it.code} already exists.") }

      insertInto(ROOMS)
        .set(ROOMS.CODE, roomCreation.code)
        .set(ROOMS.TITLE, roomCreation.title)
        .set(ROOMS.STATUS, RoomStatus.not_ready)
        .set(ROOMS.CREATED_AT, nowAtCHOffsetDateTime())
        .set(ROOMS.UPDATED_AT, nowAtCHOffsetDateTime())
        .returning()
        .fetchOne()
        ?.toRoomManagementDetail(0, 0, 0, null)
        ?: throw APIException.ServerError("Could not create a new room")
    }
  }

  fun patchRoom(room: RoomPatch): RoomManagementDetail {
    return jooq {

      update(ROOMS)
        .set(ROOMS.TITLE, room.title)
        .set(ROOMS.DESCRIPTION, room.description)
        .set(ROOMS.QUESTION, room.question)
        .set(ROOMS.TIME_LIMIT_MINUTES, room.timeLimitMinutes)
        .set(ROOMS.SINGLE_DEVICE_ROOM, nvl(room.singleDeviceRoom, ROOMS.SINGLE_DEVICE_ROOM))
        .set(ROOMS.AUTO_START_NEXT_ROOM, nvl(room.autoStartNextRoom, ROOMS.AUTO_START_NEXT_ROOM))
        .set(ROOMS.WITH_GROUP_INFO, nvl(room.withGroupInformation, ROOMS.WITH_GROUP_INFO))
        .set(ROOMS.WITH_GROUP_INFO_TEXT, room.withGroupInformationText)
        .set(ROOMS.UPDATED_AT, nowAtCHOffsetDateTime())
        .where(ROOMS.CODE.eq(room.code))
        .returning()
        .fetchOne()
        ?: APIException.RoomNotFound(room.code)

      getRoomsForManagement(room.code).first()
    }
  }

  fun upsertRoomImage(image: RoomImage, roomCode: String): RoomImage {
    return jooq {
      insertInto(ROOM_IMAGES)
        .set(image
          .toRecord(roomCode)
          .also {
            // simply way of setting the updatedAt without trigger
            it.createdAt = nowAtCHOffsetDateTime()
            it.updatedAt = nowAtCHOffsetDateTime()
          }
        )
        .onConflict(ROOM_IMAGES.ID)
        .doUpdate()
        .set(
          image
            .toRecord(roomCode)
            .also { it.updatedAt = nowAtCHOffsetDateTime() } // simply way of setting the updatedAt without trigger
        )
        .returning()
        .map { it.into(ROOM_IMAGES).toDomain() }
        .first()
    }
  }

  fun deleteRoom(code: String) {
    jooq {
      deleteFrom(ROOMS)
        .where(ROOMS.CODE.eq(code))
        .returning()
        .firstOrNull()
        ?: throw APIException.ServerError("Could not delete room $code")
    }
  }

  fun updateStatus(code: String, status: RoomStatus): RoomManagementDetail {
    jooq {
      update(ROOMS)
        .set(ROOMS.STATUS, status)
        .set(ROOMS.UPDATED_AT, nowAtCHOffsetDateTime())
        .where(ROOMS.CODE.eq(code))
        .returning()
        .fetchOne()
        ?: throw APIException.ServerError("Could not change the room status of room $code to $status")
    }
    return getRoomsForManagement(code).first()
  }

  fun getImage(imageId: Uuid): RoomImage {
    return jooq {
      selectFrom(ROOM_IMAGES)
        .where(ROOM_IMAGES.ID.eq(imageId))
        .fetchOne { it.toDomain() }
        ?: throw APIException.NotFound("There is no image with id $imageId")
    }
  }

  fun deleteImage(imageId: Uuid) {
    jooq {
      delete(ROOM_IMAGES).where(ROOM_IMAGES.ID.eq(imageId)).execute()
    }
  }

  fun upsertRoomInteractionInfo(
    code: String,
    interactionInfo: RoomInteractionInfo,
    variant: RoomStatementVariant
  ): RoomManagementDetail {
    jooq {
      update(ROOMS)
        .let {
          when (variant) {
            RoomStatementVariant.INTRO ->
              it.set(ROOMS.INTRO_TEXT, interactionInfo.text)
                .set(ROOMS.INTRO_VIDEO_TITLE, interactionInfo.videoTitle)
                .set(ROOMS.INTRO_VIDEO_URL, interactionInfo.videoURl)
                .set(ROOMS.UPDATED_AT, nowAtCHOffsetDateTime())

            RoomStatementVariant.OUTRO ->
              it.set(ROOMS.OUTRO_TEXT, interactionInfo.text)
                .set(ROOMS.OUTRO_VIDEO_TITLE, interactionInfo.videoTitle)
                .set(ROOMS.OUTRO_VIDEO_URL, interactionInfo.videoURl)
                .set(ROOMS.UPDATED_AT, nowAtCHOffsetDateTime())
          }
        }
        .where(ROOMS.CODE.eq(code))
        .returning()
        .fetchOne()
        ?: throw APIException.ServerError("could not update data for $variant")
    }
    return getRoomForManagement(code)
  }
}

fun RoomsRecord.toDomain() = Room(
  code = code!!,
  roomStatus = status!!.toDomain(),
  title = title!!,
  description = description,
  question = question,
  timeLimitMinutes = timeLimitMinutes,
  singleDeviceRoom = singleDeviceRoom!!,
  autoStartNextRoom = autoStartNextRoom!!,
  withGroupInformation = withGroupInfo!!,
  withGroupInformationText = withGroupInfoText,
  intro = RoomInteractionInfo(introText, introVideoTitle, introVideoUrl),
  outro = RoomInteractionInfo(outroText, outroVideoTitle, outroVideoUrl),
  images = listOf()
)

fun RoomsRecord.toRoomManagementDetail(
  participants: Int,
  participantsWithoutAnswers: Int,
  answers: Int,
  groups: Int?
) = RoomManagementDetail(
  code = code!!,
  roomStatus = status!!.toDomain(),
  title = title!!,
  description = description,
  question = question,
  timeLimitMinutes = timeLimitMinutes,
  singleDeviceRoom = singleDeviceRoom!!,
  autoStartNextRoom = autoStartNextRoom!!,
  withGroupInformation = withGroupInfo!!,
  withGroupInformationText = withGroupInfoText,
  intro = RoomInteractionInfo(introText, introVideoTitle, introVideoUrl),
  outro = RoomInteractionInfo(outroText, outroVideoTitle, outroVideoUrl),
  images = listOf(),
  participants = participants,
  participantsWithoutAnswers = participantsWithoutAnswers,
  answers = answers,
  groups = groups
)

fun Room.toRecord() = RoomsRecord().also {
  it.code = code
  it.status = roomStatus.toRecord()
  it.title = title
  it.description = description
  it.question = question
  it.timeLimitMinutes = timeLimitMinutes
  it.singleDeviceRoom = singleDeviceRoom
  it.autoStartNextRoom = autoStartNextRoom
  it.introText = intro.text
  it.introVideoTitle = intro.videoTitle
  it.introVideoUrl = intro.videoURl
  it.outroText = outro.text
  it.outroVideoTitle = outro.videoTitle
  it.outroVideoUrl = outro.videoURl
}

fun RoomImage.toRecord(roomCode: String) = RoomImagesRecord().also {
  it.id = id
  it.title = title
  it.url = url
  it.file = true // ToDo: currently only file upload, no web url
  it.roomCode = roomCode
}

fun RoomImagesRecord.toDomain() = RoomImage(
  id = id!!,
  title = title!!,
  url = url!!
)

fun RoomStatusJooq.toDomain() = RoomStatusDomain.valueOf(literal.uppercase())
fun RoomStatusDomain.toRecord() = RoomStatusJooq.valueOf(name.lowercase())