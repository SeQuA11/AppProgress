package com.appprogress.core

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Идентификаторы создаём на устройстве (UUID), а не в базе: так запись получает id даже офлайн. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
