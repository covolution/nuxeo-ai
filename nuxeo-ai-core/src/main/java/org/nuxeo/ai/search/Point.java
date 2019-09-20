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

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Point {

    private final byte x;

    private final byte y;

    private final int box;

    @JsonCreator
    public Point(@JsonProperty("x") byte x, @JsonProperty("y") byte y, @JsonProperty("box") int box) {
        this.x = x;
        this.y = y;
        this.box = box;
    }

    public byte getX() {
        return x;
    }

    public byte getY() {
        return y;
    }

    public int getBox() {
        return box;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Point point = (Point) o;
        return x == point.x &&
                y == point.y &&
                box == point.box;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, box);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Point{");
        sb.append("x=").append(x);
        sb.append(", y=").append(y);
        sb.append(", box=").append(box);
        sb.append('}');
        return sb.toString();
    }
}

