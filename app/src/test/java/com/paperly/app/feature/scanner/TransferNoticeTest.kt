package com.paperly.app.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferNoticeTest {
    @Test fun doneIsMovedForAMoveAndCopiedForACopy() {
        assertEquals(TransferNotice.MOVED, TransferResult.DONE.toNotice(TransferMode.MOVE))
        assertEquals(TransferNotice.COPIED, TransferResult.DONE.toNotice(TransferMode.COPY))
    }

    @Test fun partialMoveKeepsTheOriginalsAndSaysSo() {
        assertEquals(TransferNotice.PARTIAL_MOVE, TransferResult.PARTIAL.toNotice(TransferMode.MOVE))
        assertEquals(TransferNotice.PARTIAL_COPY, TransferResult.PARTIAL.toNotice(TransferMode.COPY))
    }

    @Test fun problemsMapTheSameForBothModes() {
        TransferMode.entries.forEach { mode ->
            assertEquals(TransferNotice.NOT_EDITABLE, TransferResult.NOT_EDITABLE.toNotice(mode))
            assertEquals(TransferNotice.DUPLICATE, TransferResult.DUPLICATE.toNotice(mode))
            assertEquals(TransferNotice.FAILED, TransferResult.FAILED.toNotice(mode))
        }
    }
}
