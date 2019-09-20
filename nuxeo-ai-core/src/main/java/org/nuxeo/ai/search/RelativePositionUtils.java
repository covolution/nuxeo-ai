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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility methods for calculating relative position of points on images.
 */
public class RelativePositionUtils {

    public static final String PICTURE_WIDTH = "picture:info/width";

    public static final String PICTURE_HEIGHT = "picture:info/height";

    public static final byte DEFAULT_DIVISIONS = 12;

    public static final Set<Integer> THIRD_1 = IntStream.rangeClosed(1, 4).boxed().collect(Collectors.toSet());

    public static final Set<Integer> THIRD_2 = IntStream.rangeClosed(5, 8).boxed().collect(Collectors.toSet());

    public static final Set<Integer> THIRD_3 = IntStream.rangeClosed(9, 12).boxed().collect(Collectors.toSet());

    public static final Set<Integer> QUARTER_1 = IntStream.rangeClosed(1, 3).boxed().collect(Collectors.toSet());

    public static final Set<Integer> QUARTER_2 = IntStream.rangeClosed(4, 6).boxed().collect(Collectors.toSet());

    public static final Set<Integer> QUARTER_3 = IntStream.rangeClosed(7, 9).boxed().collect(Collectors.toSet());

    public static final Set<Integer> QUARTER_4 = IntStream.rangeClosed(10, 12).boxed().collect(Collectors.toSet());

    public static final Set<Integer> THIRD_RULE_INTERSECT_1 = IntStream.rangeClosed(4, 5).boxed()
                                                                       .collect(Collectors.toSet());

    public static final Set<Integer> THIRD_RULE_INTERSECT_2 = IntStream.rangeClosed(8, 9).boxed()
                                                                       .collect(Collectors.toSet());

    public static final Set<Integer> CENTER_BOX_SMALL = new HashSet(Arrays.asList(66, 67, 78, 79));

    public static final Set<Integer> CENTER_BOX_MEDIUM = new HashSet(Arrays.asList(53, 54, 55, 56,
                                                                                   65, 66, 67, 68,
                                                                                   77, 78, 79, 80,
                                                                                   89, 90, 91, 92));

    public static final Set<Integer> CENTER_BOX_LARGE = new HashSet(Arrays.asList(40, 41, 52, 43, 44, 45,
                                                                                  52, 53, 54, 55, 56, 57,
                                                                                  64, 65, 66, 67, 68, 68,
                                                                                  76, 77, 78, 79, 80, 81,
                                                                                  88, 89, 90, 91, 92, 93,
                                                                                  101, 102, 103, 104, 105, 106));


    /**
     * @param pointx
     * @param pointY
     * @param width
     * @param height
     * @param division
     * @return
     */
    public static int getBoxPosition(int pointx, int pointY, int width, int height, byte division) {
        return getPosition(pointx, pointY, width, height, division, division);
    }

    public static Point getCentrePosition(float ratioX, float ratioY, float ratioWidth, float ratioHeight, int width, int height) {

        if (width <= 0 || height <= 0) {
            return null;
        }
        float x = ratioX * width;
        float y = ratioY * height;
        float widthX = (ratioWidth * width) + x;
        float heightX = (ratioHeight * height) + y;

        Float centreX = x + ((widthX - x) / 2);
        Float centreY = y + ((heightX - y) / 2);
        byte posX = getPosition(centreX.intValue(), width, DEFAULT_DIVISIONS);
        byte posY = getPosition(centreY.intValue(), height, DEFAULT_DIVISIONS);
        Integer posBox = posX + (posY * DEFAULT_DIVISIONS);
        Point p = new Point(posX, posY, Integer.valueOf(
                getBoxPosition(centreX.intValue(), centreY.intValue(), width, height, DEFAULT_DIVISIONS)).byteValue());
        return p;
    }

    public static Point getCentrePoint(float ratioX, float ratioY, float ratioWidth, float ratioHeight) {
        // Find the box centre
        float centreX = ratioX + (ratioWidth / 2);
        float centreY = ratioY + (ratioHeight / 2);
        return getPoint(centreX, centreY);
    }

    /**
     * Find which box the point is in
     */
    public static Point getPoint(float ratioX, float ratioY) {
        try {
            byte posX = (byte) Math.ceil(ratioX * DEFAULT_DIVISIONS);
            byte posY = (byte) Math.ceil(ratioY * DEFAULT_DIVISIONS);
            int box =  (posX + ((posY - 1) * DEFAULT_DIVISIONS));
            return new Point(posX, posY, box);
        } catch (NumberFormatException | ClassCastException e) {
            //SHOULD LOG TO DEBUG
        }
        return null;
    }

    public static int getPosition(int pointX, int pointY, int width, int height, byte divX, byte divY) {
        byte posX = getPosition(pointX, width, divX);
        byte posY = getPosition(pointY, height, divY);

        return (byte) posX + ((posY - 1) * divX);
    }

    public static boolean topHalf(int pointY) {
        return pointY <= 6;
    }

    public static boolean bottomHalf(int pointY) {
        return !topHalf(pointY);
    }

    public static boolean topHalfBox(int box) {
        return box <= (6 * 12);
    }

    public static boolean bottomHalfBox(int box) {
        return !topHalfBox(box);
    }

    public static boolean left(int pointX) {
        return pointX <= 6;
    }

    public static boolean right(int pointX) {
        return pointX > 6;
    }

    public static boolean inCenterSmall(int box) {
        return CENTER_BOX_SMALL.contains(box);
    }

    public static boolean inCenterMedium(int box) {
        return CENTER_BOX_MEDIUM.contains(box);
    }

    public static boolean inCenterLarge(int box) {
        return CENTER_BOX_LARGE.contains(box);
    }

    public static int ruleIntersect(int boxX, int boxY) {
        if (THIRD_RULE_INTERSECT_1.contains(boxX)) {
            if (THIRD_RULE_INTERSECT_1.contains(boxY)) {
                return 1;
            }
            if (THIRD_RULE_INTERSECT_2.contains(boxY)) {
                return 2;
            }
        }

        if (THIRD_RULE_INTERSECT_2.contains(boxX)) {
            if (THIRD_RULE_INTERSECT_1.contains(boxY)) {
                return 3;
            }
            if (THIRD_RULE_INTERSECT_2.contains(boxY)) {
                return 4;
            }
        }

        return -1;
    }

    public static byte getPosition(int point, int range, byte divisions) {
        int divided = range / divisions;
        int lower = 0;
        int upper = divided;
        for (byte i = 1; i <= divisions; i++) {
            if (point >= lower && point <= upper) {
                return i;
            } else {
                lower = upper;
                upper += divided;
            }
        }

        throw new IllegalArgumentException("Point isn't within the range");
    }

    public static byte getPosition(float point, byte divisions) {
        return Float.valueOf(point * divisions).byteValue();
    }

    public static int getWidth(Map<String, String> properties) {
        return getIntValue(properties, PICTURE_WIDTH);
    }

    public static int getHeight(Map<String, String> properties) {
        return getIntValue(properties, PICTURE_HEIGHT);
    }

    public static int getIntValue(Map<String, String> properties, String key) {
        String value = properties.get(key);
        try {
            if (isNotBlank(value)) {
                return Integer.valueOf(value);
            }
        } catch (NumberFormatException e) {
            // Invalid number
        }
        return -1;
    }
}
