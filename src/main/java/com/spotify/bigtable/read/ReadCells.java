/*-
 * -\-\-
 * simple-bigtable
 * --
 * Copyright (C) 2016 - 2017 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

/*
 *
 *  * Copyright 2016 Spotify AB.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package com.spotify.bigtable.read;

import com.google.bigtable.v2.Cell;
import com.google.bigtable.v2.Column;
import com.google.bigtable.v2.Family;
import com.google.bigtable.v2.Row;
import com.google.bigtable.v2.RowFilter;
import com.google.bigtable.v2.TimestampRange;
import com.google.bigtable.v2.ValueRange;
import com.google.protobuf.ByteString;
import com.spotify.bigtable.read.ReadCell.CellWithinCellsRead;
import com.spotify.bigtable.read.ReadCell.CellWithinColumnsRead;
import com.spotify.bigtable.read.ReadCell.CellWithinFamiliesRead;
import com.spotify.bigtable.read.ReadCell.CellWithinRowsRead;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class ReadCells {

  interface CellsRead<MultiCellT, OneCellT, R> extends BigtableRead<R> {

    OneCellT latest();

    MultiCellT limit(final int limit);

    MultiCellT startTimestampMicros(final long startTimestampMicros);

    MultiCellT endTimestampMicros(final long endTimestampMicros);

    MultiCellT valueRegex(final ByteString valueRegex);

    MultiCellT startValueClosed(final ByteString startValueInclusive);

    MultiCellT startValueOpen(final ByteString startValueExclusive);

    MultiCellT endValueClosed(final ByteString endValueInclusive);

    MultiCellT endValueOpen(final ByteString endValueExclusive);
  }

  public interface CellsWithinColumnRead
      extends CellsRead<CellsWithinColumnRead, CellWithinCellsRead, List<Cell>> {
    class ReadImpl
        extends AbstractCellsRead<
        CellsWithinColumnRead,
        CellWithinCellsRead,
        List<Cell>,
        Optional<Column>>
        implements CellsWithinColumnRead {

      public ReadImpl(final Internal<Optional<Column>> column) {
        super(column);
      }

      @Override
      protected CellsWithinColumnRead multiCell() {
        return this;
      }

      @Override
      public CellWithinCellsRead latest() {
        return new CellWithinCellsRead.ReadImpl(this);
      }

      @Override
      protected Function<Optional<Column>, List<Cell>> parentTypeToCurrentType() {
        return colOpt -> colOpt.map(Column::getCellsList).orElse(Collections.emptyList());
      }
    }
  }

  public interface CellsWithinColumnsRead
      extends CellsRead<CellsWithinColumnsRead, CellWithinColumnsRead, List<Column>> {
    class ReadImpl
        extends MultiReadImpl<CellsWithinColumnsRead, CellWithinColumnsRead, Column>
        implements CellsWithinColumnsRead {

      public ReadImpl(final Internal<List<Column>> parentRead) {
        super(parentRead);
      }

      @Override
      protected CellsWithinColumnsRead multiCell() {
        return this;
      }

      @Override
      public CellWithinColumnsRead latest() {
        return new CellWithinColumnsRead.ReadImpl(this);
      }
    }
  }

  public interface CellsWithinFamiliesRead
      extends CellsRead<CellsWithinFamiliesRead, CellWithinFamiliesRead, List<Family>> {
    class ReadImpl
        extends MultiReadImpl<CellsWithinFamiliesRead, CellWithinFamiliesRead, Family>
        implements CellsWithinFamiliesRead {

      public ReadImpl(final BigtableRead.Internal<List<Family>> parent) {
        super(parent);
      }

      @Override
      protected CellsWithinFamiliesRead multiCell() {
        return this;
      }

      @Override
      public CellWithinFamiliesRead latest() {
        return new CellWithinFamiliesRead.ReadImpl(this);
      }
    }
  }
  
  public interface CellsWithinRowsRead
      extends CellsRead<CellsWithinRowsRead, CellWithinRowsRead, List<Row>> {
    class ReadImpl
        extends MultiReadImpl<CellsWithinRowsRead, CellWithinRowsRead, Row>
        implements CellsWithinRowsRead {

      public ReadImpl(final Internal<List<Row>> parent) {
        super(parent);
      }

      @Override
      protected CellsWithinRowsRead multiCell() {
        return this;
      }

      @Override
      public CellWithinRowsRead latest() {
        return new CellWithinRowsRead.ReadImpl(this);
      }
    }
  }

  private abstract static class MultiReadImpl<MultiCellT, OneCellT, R>
      extends AbstractCellsRead<MultiCellT, OneCellT, List<R>, List<R>> {

    private MultiReadImpl(final Internal<List<R>> parentRead) {
      super(parentRead);
    }

    @Override
    protected Function<List<R>, List<R>> parentTypeToCurrentType() {
      return Function.identity();
    }
  }

  private abstract static class AbstractCellsRead<MultiCellT, OneCellT, R, P>
      extends AbstractBigtableRead<P, R> implements CellsRead<MultiCellT, OneCellT, R> {

    private AbstractCellsRead(final Internal<P> parentRead) {
      super(parentRead);
    }

    protected abstract MultiCellT multiCell();

    @Override
    public MultiCellT limit(final int limit) {
      final RowFilter.Builder limitFilter = RowFilter.newBuilder()
          .setCellsPerColumnLimitFilter(limit);
      addRowFilter(limitFilter);
      return multiCell();
    }

    @Override
    public MultiCellT startTimestampMicros(final long startTimestampMicros) {
      final TimestampRange tsRange = TimestampRange.newBuilder()
          .setStartTimestampMicros(startTimestampMicros)
          .build();
      addRowFilter(RowFilter.newBuilder().setTimestampRangeFilter(tsRange));
      return multiCell();
    }

    @Override
    public MultiCellT endTimestampMicros(final long endTimestampMicros) {
      final TimestampRange tsRange = TimestampRange.newBuilder()
          .setEndTimestampMicros(endTimestampMicros)
          .build();
      addRowFilter(RowFilter.newBuilder().setTimestampRangeFilter(tsRange));
      return multiCell();
    }

    @Override
    public MultiCellT valueRegex(final ByteString valueRegex) {
      addRowFilter(RowFilter.newBuilder().setValueRegexFilter(valueRegex));
      return multiCell();
    }

    @Override
    public MultiCellT startValueClosed(final ByteString startValueClosed) {
      final ValueRange.Builder valueRange = ValueRange.newBuilder()
          .setStartValueClosed(startValueClosed);
      addRowFilter(RowFilter.newBuilder().setValueRangeFilter(valueRange));
      return multiCell();
    }

    @Override
    public MultiCellT startValueOpen(final ByteString startValueOpen) {
      final ValueRange.Builder valueRange = ValueRange.newBuilder()
          .setStartValueOpen(startValueOpen);
      addRowFilter(RowFilter.newBuilder().setValueRangeFilter(valueRange));
      return multiCell();
    }

    @Override
    public MultiCellT endValueClosed(final ByteString endValueClosed) {
      final ValueRange.Builder valueRange = ValueRange.newBuilder()
          .setEndValueClosed(endValueClosed);
      addRowFilter(RowFilter.newBuilder().setValueRangeFilter(valueRange));
      return multiCell();
    }

    @Override
    public MultiCellT endValueOpen(final ByteString endValueOpen) {
      final ValueRange.Builder valueRange = ValueRange.newBuilder()
          .setEndValueOpen(endValueOpen);
      addRowFilter(RowFilter.newBuilder().setValueRangeFilter(valueRange));
      return multiCell();
    }
  }
}
