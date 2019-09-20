/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.search;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.nuxeo.ai.search.RelativePositionUtils.DEFAULT_DIVISIONS;
import static org.nuxeo.ai.search.RelativePositionUtils.bottomHalfBox;
import static org.nuxeo.ai.search.RelativePositionUtils.getBoxPosition;
import static org.nuxeo.ai.search.RelativePositionUtils.getCentrePoint;
import static org.nuxeo.ai.search.RelativePositionUtils.getCentrePosition;
import static org.nuxeo.ai.search.RelativePositionUtils.getPosition;
import static org.nuxeo.ai.search.RelativePositionUtils.inCenterLarge;
import static org.nuxeo.ai.search.RelativePositionUtils.inCenterMedium;
import static org.nuxeo.ai.search.RelativePositionUtils.inCenterSmall;
import static org.nuxeo.ai.search.RelativePositionUtils.left;
import static org.nuxeo.ai.search.RelativePositionUtils.right;
import static org.nuxeo.ai.search.RelativePositionUtils.ruleIntersect;
import static org.nuxeo.ai.search.RelativePositionUtils.topHalfBox;

import org.junit.Test;

public class TestRelativePosition {

    @Test
    public void testImage() {
        int x = getPosition(715, 960, DEFAULT_DIVISIONS);
        int y = getPosition(295, 720, DEFAULT_DIVISIONS);
        assertEquals(9, x);
        assertEquals(5, y);

        int box = getBoxPosition(715, 295,960, 720, DEFAULT_DIVISIONS);
        assertEquals(57, box);
        assertEquals(3  , ruleIntersect(x, y));
        assertFalse(inCenterSmall(box));
        assertFalse(inCenterMedium(box));
        assertTrue(inCenterLarge(box));

        assertTrue(topHalfBox(box));
        assertFalse(bottomHalfBox(box));
        assertFalse(left(x));
        assertTrue(right(x));

        x = getPosition(555, 960, DEFAULT_DIVISIONS);
        y = getPosition(415, 720, DEFAULT_DIVISIONS);
        assertEquals(7, x);
        assertEquals(7, y);
        box = getBoxPosition(555, 415,960, 720, DEFAULT_DIVISIONS);
        assertEquals(79, box);
        assertEquals(-1  , ruleIntersect(x, y));
        assertTrue(inCenterSmall(box));
        assertTrue(inCenterMedium(box));
        assertTrue(inCenterLarge(box));
        assertFalse(topHalfBox(box));
        assertTrue(bottomHalfBox(box));
        assertFalse(left(x));
        assertTrue(right(x));

        // Balmer
        //are the first 2 the right way round?
        //left, top, width, height
        Point theBox = getCentrePosition(0.17831402f, 0.12244074f, 0.21573015f, 0.45839068f, 640, 427);
        Point theBoxNoHeight = getCentrePoint(0.17831402f, 0.12244074f, 0.21573015f, 0.45839068f);
        x = getPosition(114, 640, DEFAULT_DIVISIONS);
        y = getPosition(52, 427, DEFAULT_DIVISIONS);
        assertEquals(3, x);
        assertEquals(2, y);
        box = getBoxPosition(114, 52, 640, 427, DEFAULT_DIVISIONS);
        assertEquals(15, box);
    }
    @Test
    public void testPosition() {
        assertEquals(1, getPosition(15, 100, (byte) 4));
        assertEquals(1, getPosition(25, 100, (byte) 4));
        assertEquals(2, getPosition(26, 100, (byte) 4));
        assertEquals(2, getPosition(50, 100, (byte) 4));
        assertEquals(3, getPosition(75, 100, (byte) 4));
        assertEquals(4, getPosition(100, 100, (byte) 4));

        assertEquals(1, getPosition(33, 100, (byte) 3));
        assertEquals(2, getPosition(34, 100, (byte) 3));
        assertEquals(3, getPosition(67, 100, (byte) 3));

        assertEquals(8, getPosition(45, 75, 90, 90, (byte) 3, (byte) 3));
        assertEquals(9, getPosition(75, 75, 90, 90, (byte) 3, (byte) 3));
        assertEquals(3, getPosition(10, 75, 100, 100, (byte) 2, (byte) 2));
        assertEquals(1, getPosition(5, 25, 90, 90, (byte) 3, (byte) 3));
        assertEquals(3, getPosition(75, 25, 90, 90, (byte) 3, (byte) 3));
        assertEquals(5, getPosition(50, 50, 90, 90, (byte) 3, (byte) 3));

        //    assertEquals(3, getBox(67, 100, (byte) 2));
    }
}
