package com.ailover.app.data.local.converter

import androidx.room.TypeConverter

enum class SenderType {
    USER,
    AI,
    SYSTEM
}

enum class MessageType {
    TEXT,
    VOICE,
    EMOTE
}

class Converters {
    @TypeConverter
    fun fromSenderType(value: SenderType): String = value.name

    @TypeConverter
    fun toSenderType(value: String): SenderType = SenderType.valueOf(value)

    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)
}
