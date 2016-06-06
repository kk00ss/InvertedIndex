// Copyright 2016 Konstantin Voznesenskiy

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//    http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package rhinodog.Core.Iterators

import rhinodog.Core.Definitions.BaseTraits.TermIteratorBase

import scala.collection.mutable.ArrayBuffer

class LayeredIterator(layers: Seq[TermIteratorBase]) extends TermIteratorBase {

    var positionChanged = true
    var _currentDocID: Long = -1
    var _currentScore: Float = -1

    updateSmallestPosition()

    def currentDocID: Long = _currentDocID

    def currentScore: Float = {
        if (positionChanged) {
            computeScore()
        }
        _currentScore
    }

    def computeScore() = {
        _currentScore = 0
        //there should be exactly one layer with particular docID
        _currentScore = layers.find(_.currentDocID == _currentDocID)
                              .get.currentScore
        positionChanged = false
    }

    def hasNextBlock: Boolean = layers.exists(_.hasNextBlock)

    def hasNextSegment: Boolean = layers.exists(_.hasNextSegment)

    def hasNext: Boolean = layers.exists(_.hasNext)

    def blockMaxDocID: Long = layers.minBy(_.blockMaxDocID).blockMaxDocID

    def blockMaxScore: Float = layers.map(_.blockMaxScore).sum

    def segmentMaxDocID: Long = layers.minBy(_.segmentMaxDocID).blockMaxDocID

    def segmentMaxScore: Float = layers.map(_.segmentMaxScore).sum

    var movedComponents = ArrayBuffer[TermIteratorBase]()

    def nextBlock() = {
        val componentToMove = layers.minBy(_.blockMaxDocID)
        componentToMove.nextBlock()
        componentToMove.nextSegmentMeta()
        if (!movedComponents.contains(componentToMove))
            movedComponents += componentToMove
    }

    def nextSegmentMeta() = {
        val componentToMove = layers.minBy(_.segmentMaxDocID)
        componentToMove.nextSegmentMeta()
        if (!movedComponents.contains(componentToMove))
            movedComponents += componentToMove
    }

    def initSegmentIterator() = {
        movedComponents.foreach(_.initSegmentIterator())
        movedComponents.clear()
    }

    def advanceToScore(targetScore: Float): Long = {
        layers.foreach(_.advanceToScore(targetScore))
        positionChanged = true
        updateSmallestPosition()
    }

    def updateSmallestPosition(): Long = {
        var smallestPosition = Long.MaxValue
        for (i <- layers.indices)
            if (layers(i).currentDocID != -1 && layers(i).currentDocID < smallestPosition)
                smallestPosition = layers(i).currentDocID
        //if smallestPosition == 0 it means that all iterators run out of elements
        if (smallestPosition != Long.MaxValue)
            _currentDocID = smallestPosition
        else _currentDocID = -1
        return _currentDocID
    }

    def next(): Long = {
        for (i <- layers.indices)
            if (layers(i).currentDocID == _currentDocID) {
                layers(i).next()
            }
        positionChanged = true
        updateSmallestPosition()
    }

    def advance(targetDocID: Long): Long = {
        if (targetDocID != _currentDocID) {
            layers.foreach(_.advance(targetDocID))
            positionChanged = true
            updateSmallestPosition()
        }
        _currentDocID
    }
}