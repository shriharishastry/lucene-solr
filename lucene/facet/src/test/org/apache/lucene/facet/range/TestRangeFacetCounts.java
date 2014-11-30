package org.apache.lucene.facet.range;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldTypes;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.DrillSideways.DrillSidewaysResult;
import org.apache.lucene.facet.DrillSideways;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetTestCase;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.MultiFacets;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.DoubleDocValues;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilterCachingPolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BitDocIdSet;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.TestUtil;


public class TestRangeFacetCounts extends FacetTestCase {

  public void testBasicLong() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    for(long l=0;l<100;l++) {
      Document doc = w.newDocument();
      doc.addLong("field", l);
      w.addDocument(doc);
    }

    // Also add Long.MAX_VALUE
    Document doc = w.newDocument();
    doc.addLong("field", Long.MAX_VALUE);
    w.addDocument(doc);

    IndexReader r = w.getReader();
    w.close();

    FacetsCollector fc = new FacetsCollector();
    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);

    Facets facets = new LongRangeFacetCounts("field", fc,
        new LongRange("less than 10", 0L, true, 10L, false),
        new LongRange("less than or equal to 10", 0L, true, 10L, true),
        new LongRange("over 90", 90L, false, 100L, false),
        new LongRange("90 or above", 90L, true, 100L, false),
        new LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true));

    FacetResult result = facets.getTopChildren(10, "field");
    assertEquals("dim=field path=[] value=22 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (1)\n",
                 result.toString());
    
    r.close();
    d.close();
  }

  @SuppressWarnings("unused")
  public void testUselessRange() {
    try {
      new LongRange("useless", 7, true, 6, true);
      fail("did not hit expected exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
    try {
      new LongRange("useless", 7, true, 7, false);
      fail("did not hit expected exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
    try {
      new DoubleRange("useless", 7.0, true, 6.0, true);
      fail("did not hit expected exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
    try {
      new DoubleRange("useless", 7.0, true, 7.0, false);
      fail("did not hit expected exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  public void testLongMinMax() throws Exception {

    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);

    Document doc = w.newDocument();
    doc.addLong("field", Long.MIN_VALUE);
    w.addDocument(doc);

    doc = w.newDocument();
    doc.addLong("field", 0L);
    w.addDocument(doc);

    doc = w.newDocument();
    doc.addLong("field", Long.MAX_VALUE);
    w.addDocument(doc);

    IndexReader r = w.getReader();
    w.close();

    FacetsCollector fc = new FacetsCollector();
    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);

    Facets facets = new LongRangeFacetCounts("field", fc,
        new LongRange("min", Long.MIN_VALUE, true, Long.MIN_VALUE, true),
        new LongRange("max", Long.MAX_VALUE, true, Long.MAX_VALUE, true),
        new LongRange("all0", Long.MIN_VALUE, true, Long.MAX_VALUE, true),
        new LongRange("all1", Long.MIN_VALUE, false, Long.MAX_VALUE, true),
        new LongRange("all2", Long.MIN_VALUE, true, Long.MAX_VALUE, false),
        new LongRange("all3", Long.MIN_VALUE, false, Long.MAX_VALUE, false));

    FacetResult result = facets.getTopChildren(10, "field");
    assertEquals("dim=field path=[] value=3 childCount=6\n  min (1)\n  max (1)\n  all0 (3)\n  all1 (2)\n  all2 (2)\n  all3 (1)\n",
                 result.toString());
    
    r.close();
    d.close();
  }

  public void testOverlappedEndStart() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    for(long l=0;l<100;l++) {
      Document doc = w.newDocument();
      doc.addLong("field", l);
      w.addDocument(doc);
    }
    Document doc = w.newDocument();
    doc.addLong("field", Long.MAX_VALUE);
    w.addDocument(doc);

    IndexReader r = w.getReader();
    w.close();

    FacetsCollector fc = new FacetsCollector();
    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);

    Facets facets = new LongRangeFacetCounts("field", fc,
        new LongRange("0-10", 0L, true, 10L, true),
        new LongRange("10-20", 10L, true, 20L, true),
        new LongRange("20-30", 20L, true, 30L, true),
        new LongRange("30-40", 30L, true, 40L, true));
    
    FacetResult result = facets.getTopChildren(10, "field");
    assertEquals("dim=field path=[] value=41 childCount=4\n  0-10 (11)\n  10-20 (11)\n  20-30 (11)\n  30-40 (11)\n",
                 result.toString());
    
    r.close();
    d.close();
  }

  /** Tests single request that mixes Range and non-Range
   *  faceting, with DrillSideways and taxonomy. */
  public void testMixedRangeAndNonRangeTaxonomy() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    Directory td = newDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(td, IndexWriterConfig.OpenMode.CREATE);

    FacetsConfig config = new FacetsConfig();

    for (long l = 0; l < 100; l++) {
      Document doc = w.newDocument();
      // For computing range facet counts and drill down by numeric range:
      doc.addLong("field", l);

      if ((l&3) == 0) {
        doc.add(new FacetField("dim", "a"));
      } else {
        doc.add(new FacetField("dim", "b"));
      }
      w.addDocument(config.build(tw, doc));
    }

    final IndexReader r = w.getReader();
    FieldTypes fieldTypes = r.getFieldTypes();

    final TaxonomyReader tr = new DirectoryTaxonomyReader(tw);

    IndexSearcher s = newSearcher(r);

    if (VERBOSE) {
      System.out.println("TEST: searcher=" + s);
    }

    DrillSideways ds = new DrillSideways(s, config, tr) {

        @Override
        protected Facets buildFacetsResult(FacetsCollector drillDowns, FacetsCollector[] drillSideways, String[] drillSidewaysDims) throws IOException {        
          FacetsCollector dimFC = drillDowns;
          FacetsCollector fieldFC = drillDowns;
          if (drillSideways != null) {
            for(int i=0;i<drillSideways.length;i++) {
              String dim = drillSidewaysDims[i];
              if (dim.equals("field")) {
                fieldFC = drillSideways[i];
              } else {
                dimFC = drillSideways[i];
              }
            }
          }

          Map<String,Facets> byDim = new HashMap<>();
          byDim.put("field",
                    new LongRangeFacetCounts("field", fieldFC,
                          new LongRange("less than 10", 0L, true, 10L, false),
                          new LongRange("less than or equal to 10", 0L, true, 10L, true),
                          new LongRange("over 90", 90L, false, 100L, false),
                          new LongRange("90 or above", 90L, true, 100L, false),
                          new LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false)));
          byDim.put("dim", getTaxonomyFacetCounts(taxoReader, config, dimFC));
          return new MultiFacets(byDim, null);
        }

        @Override
        protected boolean scoreSubDocsAtOnce() {
          return random().nextBoolean();
        }
      };

    // First search, no drill downs:
    DrillDownQuery ddq = new DrillDownQuery(config);
    DrillSidewaysResult dsr = ds.search(null, ddq, 10);

    assertEquals(100, dsr.hits.totalHits);
    assertEquals("dim=dim path=[] value=100 childCount=2\n  b (75)\n  a (25)\n", dsr.facets.getTopChildren(10, "dim").toString());
    assertEquals("dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
                 dsr.facets.getTopChildren(10, "field").toString());

    // Second search, drill down on dim=b:
    ddq = new DrillDownQuery(config);
    ddq.add("dim", "b");
    dsr = ds.search(null, ddq, 10);

    assertEquals(75, dsr.hits.totalHits);
    assertEquals("dim=dim path=[] value=100 childCount=2\n  b (75)\n  a (25)\n", dsr.facets.getTopChildren(10, "dim").toString());
    assertEquals("dim=field path=[] value=16 childCount=5\n  less than 10 (7)\n  less than or equal to 10 (8)\n  over 90 (7)\n  90 or above (8)\n  over 1000 (0)\n",
                 dsr.facets.getTopChildren(10, "field").toString());

    // Third search, drill down on "less than or equal to 10":
    ddq = new DrillDownQuery(config);
    ddq.add("field", new ConstantScoreQuery(fieldTypes.newRangeFilter("field", 0L, true, 10L, true)));
    dsr = ds.search(null, ddq, 10);

    assertEquals(11, dsr.hits.totalHits);
    assertEquals("dim=dim path=[] value=11 childCount=2\n  b (8)\n  a (3)\n", dsr.facets.getTopChildren(10, "dim").toString());
    assertEquals("dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
                 dsr.facets.getTopChildren(10, "field").toString());
    w.close();
    IOUtils.close(tw, tr, td, r, d);
  }

  public void testBasicDouble() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    for(long l=0;l<100;l++) {
      Document doc = w.newDocument();
      doc.addDouble("field", l);
      w.addDocument(doc);
    }

    IndexReader r = w.getReader();

    FacetsCollector fc = new FacetsCollector();

    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);
    Facets facets = new DoubleRangeFacetCounts("field", fc,
        new DoubleRange("less than 10", 0.0, true, 10.0, false),
        new DoubleRange("less than or equal to 10", 0.0, true, 10.0, true),
        new DoubleRange("over 90", 90.0, false, 100.0, false),
        new DoubleRange("90 or above", 90.0, true, 100.0, false),
        new DoubleRange("over 1000", 1000.0, false, Double.POSITIVE_INFINITY, false));
                                         
    assertEquals("dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
                 facets.getTopChildren(10, "field").toString());
    w.close();
    IOUtils.close(r, d);
  }

  public void testBasicFloat() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    for(long l=0;l<100;l++) {
      Document doc = w.newDocument();
      doc.addFloat("field", l);
      w.addDocument(doc);
    }

    IndexReader r = w.getReader();

    FacetsCollector fc = new FacetsCollector();

    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);

    Facets facets = new DoubleRangeFacetCounts("field", new FloatFieldSource("field"), fc,
        new DoubleRange("less than 10", 0.0f, true, 10.0f, false),
        new DoubleRange("less than or equal to 10", 0.0f, true, 10.0f, true),
        new DoubleRange("over 90", 90.0f, false, 100.0f, false),
        new DoubleRange("90 or above", 90.0f, true, 100.0f, false),
        new DoubleRange("over 1000", 1000.0f, false, Double.POSITIVE_INFINITY, false));
    
    assertEquals("dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
                 facets.getTopChildren(10, "field").toString());
    w.close();
    IOUtils.close(r, d);
  }

  public void testRandomLongs() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int numDocs = atLeast(1000);
    if (VERBOSE) {
      System.out.println("TEST: numDocs=" + numDocs);
    }
    long[] values = new long[numDocs];
    long minValue = Long.MAX_VALUE;
    long maxValue = Long.MIN_VALUE;
    for(int i=0;i<numDocs;i++) {
      Document doc = w.newDocument();
      long v = random().nextLong();
      values[i] = v;
      doc.addLong("field", v);
      w.addDocument(doc);
      minValue = Math.min(minValue, v);
      maxValue = Math.max(maxValue, v);
    }
    IndexReader r = w.getReader();
    FieldTypes fieldTypes = r.getFieldTypes();

    IndexSearcher s = newSearcher(r);
    FacetsConfig config = new FacetsConfig();
    
    int numIters = atLeast(10);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + iter);
      }
      int numRange = TestUtil.nextInt(random(), 1, 100);
      LongRange[] ranges = new LongRange[numRange];
      int[] expectedCounts = new int[numRange];
      long minAcceptedValue = Long.MAX_VALUE;
      long maxAcceptedValue = Long.MIN_VALUE;
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        long min;
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          LongRange prevRange = ranges[random().nextInt(rangeID)];
          if (random().nextBoolean()) {
            min = prevRange.min;
          } else {
            min = prevRange.max;
          }
        } else {
          min = random().nextLong();
        }
        long max;
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          LongRange prevRange = ranges[random().nextInt(rangeID)];
          if (random().nextBoolean()) {
            max = prevRange.min;
          } else {
            max = prevRange.max;
          }
        } else {
          max = random().nextLong();
        }

        if (min > max) {
          long x = min;
          min = max;
          max = x;
        }
        boolean minIncl;
        boolean maxIncl;
        if (min == max) {
          minIncl = true;
          maxIncl = true;
        } else {
          minIncl = random().nextBoolean();
          maxIncl = random().nextBoolean();
        }
        ranges[rangeID] = new LongRange("r" + rangeID, min, minIncl, max, maxIncl);
        if (VERBOSE) {
          System.out.println("  range " + rangeID + ": " + ranges[rangeID]);      
        }

        // Do "slow but hopefully correct" computation of
        // expected count:
        for(int i=0;i<numDocs;i++) {
          boolean accept = true;
          if (minIncl) {
            accept &= values[i] >= min;
          } else {
            accept &= values[i] > min;
          }
          if (maxIncl) {
            accept &= values[i] <= max;
          } else {
            accept &= values[i] < max;
          }
          if (accept) {
            expectedCounts[rangeID]++;
            minAcceptedValue = Math.min(minAcceptedValue, values[i]);
            maxAcceptedValue = Math.max(maxAcceptedValue, values[i]);
          }
        }
      }

      FacetsCollector sfc = new FacetsCollector();
      s.search(new MatchAllDocsQuery(), sfc);
      Filter fastMatchFilter;
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchFilter = fieldTypes.newRangeFilter("field", minValue, true, maxValue, true);
        } else {
          fastMatchFilter = fieldTypes.newRangeFilter("field", minAcceptedValue, true, maxAcceptedValue, true);
        }
      } else {
        fastMatchFilter = null;
      }
      ValueSource vs = new LongFieldSource("field");
      Facets facets = new LongRangeFacetCounts("field", vs, sfc, fastMatchFilter, ranges);
      FacetResult result = facets.getTopChildren(10, "field");
      assertEquals(numRange, result.labelValues.length);
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        if (VERBOSE) {
          System.out.println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID]);
        }
        LabelAndValue subNode = result.labelValues[rangeID];
        assertEquals("r" + rangeID, subNode.label);
        assertEquals(expectedCounts[rangeID], subNode.value.intValue());

        LongRange range = ranges[rangeID];

        // Test drill-down:
        DrillDownQuery ddq = new DrillDownQuery(config);
        if (random().nextBoolean()) {
          if (random().nextBoolean()) {
            ddq.add("field", fieldTypes.newRangeFilter("field", range.min, range.minInclusive, range.max, range.maxInclusive));
          } else {
            ddq.add("field", new ConstantScoreQuery(fieldTypes.newRangeFilter("field", range.min, range.minInclusive, range.max, range.maxInclusive)));
          }
        } else {
          ddq.add("field", range.getFilter(fastMatchFilter, vs));
        }
        assertEquals(expectedCounts[rangeID], s.search(ddq, 10).totalHits);
      }
    }

    w.close();
    IOUtils.close(r, dir);
  }

  public void testRandomFloats() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int numDocs = atLeast(1000);
    float[] values = new float[numDocs];
    float minValue = Float.POSITIVE_INFINITY;
    float maxValue = Float.NEGATIVE_INFINITY;
    for(int i=0;i<numDocs;i++) {
      Document doc = w.newDocument();
      float v = random().nextFloat();
      values[i] = v;
      doc.addFloat("field", v);
      w.addDocument(doc);
      minValue = Math.min(minValue, v);
      maxValue = Math.max(maxValue, v);
    }
    IndexReader r = w.getReader();
    FieldTypes fieldTypes = r.getFieldTypes();

    IndexSearcher s = newSearcher(r);
    FacetsConfig config = new FacetsConfig();
    
    int numIters = atLeast(10);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + iter);
      }
      int numRange = TestUtil.nextInt(random(), 1, 5);
      DoubleRange[] ranges = new DoubleRange[numRange];
      int[] expectedCounts = new int[numRange];
      float minAcceptedValue = Float.POSITIVE_INFINITY;
      float maxAcceptedValue = Float.NEGATIVE_INFINITY;
      if (VERBOSE) {
        System.out.println("TEST: " + numRange + " ranges");
      }
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        double min;
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          DoubleRange prevRange = ranges[random().nextInt(rangeID)];
          if (random().nextBoolean()) {
            min = prevRange.min;
          } else {
            min = prevRange.max;
          }
        } else {
          min = random().nextDouble();
        }
        double max;
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          DoubleRange prevRange = ranges[random().nextInt(rangeID)];
          if (random().nextBoolean()) {
            max = prevRange.min;
          } else {
            max = prevRange.max;
          }
        } else {
          max = random().nextDouble();
        }

        if (min > max) {
          double x = min;
          min = max;
          max = x;
        }

        // Must truncate to float precision so that the
        // drill-down counts (which use NRQ.newFloatRange)
        // are correct:
        min = (float) min;
        max = (float) max;

        boolean minIncl;
        boolean maxIncl;
        if (min == max) {
          minIncl = true;
          maxIncl = true;
        } else {
          minIncl = random().nextBoolean();
          maxIncl = random().nextBoolean();
        }
        ranges[rangeID] = new DoubleRange("r" + rangeID, min, minIncl, max, maxIncl);

        if (VERBOSE) {
          System.out.println("TEST:   range " + rangeID + ": " + ranges[rangeID]);
        }

        // Do "slow but hopefully correct" computation of
        // expected count:
        for(int i=0;i<numDocs;i++) {
          boolean accept = true;
          if (minIncl) {
            accept &= values[i] >= min;
          } else {
            accept &= values[i] > min;
          }
          if (maxIncl) {
            accept &= values[i] <= max;
          } else {
            accept &= values[i] < max;
          }
          if (VERBOSE) {
            System.out.println("TEST:   check doc=" + i + " val=" + values[i] + " accept=" + accept);
          }
          if (accept) {
            expectedCounts[rangeID]++;
            minAcceptedValue = Math.min(minAcceptedValue, values[i]);
            maxAcceptedValue = Math.max(maxAcceptedValue, values[i]);
          }
        }
      }

      FacetsCollector sfc = new FacetsCollector();
      s.search(new MatchAllDocsQuery(), sfc);
      Filter fastMatchFilter;
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchFilter = fieldTypes.newRangeFilter("field", minValue, true, maxValue, true);
        } else {
          fastMatchFilter = fieldTypes.newRangeFilter("field", minAcceptedValue, true, maxAcceptedValue, true);
        }
      } else {
        fastMatchFilter = null;
      }
      ValueSource vs = new FloatFieldSource("field");
      Facets facets = new DoubleRangeFacetCounts("field", vs, sfc, fastMatchFilter, ranges);
      FacetResult result = facets.getTopChildren(10, "field");
      assertEquals(numRange, result.labelValues.length);
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        if (VERBOSE) {
          System.out.println("TEST: verify range " + rangeID + " expectedCount=" + expectedCounts[rangeID]);
        }
        LabelAndValue subNode = result.labelValues[rangeID];
        assertEquals("r" + rangeID, subNode.label);
        assertEquals(expectedCounts[rangeID], subNode.value.intValue());

        DoubleRange range = ranges[rangeID];

        // Test drill-down:
        DrillDownQuery ddq = new DrillDownQuery(config);
        if (random().nextBoolean()) {
          if (random().nextBoolean()) {
            ddq.add("field", fieldTypes.newRangeFilter("field", (float) range.min, range.minInclusive, (float) range.max, range.maxInclusive));
          } else {
            ddq.add("field", new ConstantScoreQuery(fieldTypes.newRangeFilter("field", (float) range.min, range.minInclusive, (float) range.max, range.maxInclusive)));
          }
        } else {
          ddq.add("field", range.getFilter(fastMatchFilter, vs));
        }
        assertEquals(expectedCounts[rangeID], s.search(ddq, 10).totalHits);
      }
    }

    w.close();
    IOUtils.close(r, dir);
  }

  public void testRandomDoubles() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int numDocs = atLeast(1000);
    double[] values = new double[numDocs];
    double minValue = Double.POSITIVE_INFINITY;
    double maxValue = Double.NEGATIVE_INFINITY;
    for(int i=0;i<numDocs;i++) {
      Document doc = w.newDocument();
      double v = random().nextDouble();
      values[i] = v;
      doc.addDouble("field", v);
      w.addDocument(doc);
      minValue = Math.min(minValue, v);
      maxValue = Math.max(maxValue, v);
    }
    IndexReader r = w.getReader();
    FieldTypes fieldTypes = r.getFieldTypes();

    IndexSearcher s = newSearcher(r);
    FacetsConfig config = new FacetsConfig();
    
    int numIters = atLeast(10);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + iter);
      }
      int numRange = TestUtil.nextInt(random(), 1, 5);
      DoubleRange[] ranges = new DoubleRange[numRange];
      int[] expectedCounts = new int[numRange];
      double minAcceptedValue = Double.POSITIVE_INFINITY;
      double maxAcceptedValue = Double.NEGATIVE_INFINITY;
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        double min;
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          DoubleRange prevRange = ranges[random().nextInt(rangeID)];
          if (random().nextBoolean()) {
            min = prevRange.min;
          } else {
            min = prevRange.max;
          }
        } else {
          min = random().nextDouble();
        }
        double max;
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          DoubleRange prevRange = ranges[random().nextInt(rangeID)];
          if (random().nextBoolean()) {
            max = prevRange.min;
          } else {
            max = prevRange.max;
          }
        } else {
          max = random().nextDouble();
        }

        if (min > max) {
          double x = min;
          min = max;
          max = x;
        }

        boolean minIncl;
        boolean maxIncl;
        if (min == max) {
          minIncl = true;
          maxIncl = true;
        } else {
          minIncl = random().nextBoolean();
          maxIncl = random().nextBoolean();
        }
        ranges[rangeID] = new DoubleRange("r" + rangeID, min, minIncl, max, maxIncl);

        // Do "slow but hopefully correct" computation of
        // expected count:
        for(int i=0;i<numDocs;i++) {
          boolean accept = true;
          if (minIncl) {
            accept &= values[i] >= min;
          } else {
            accept &= values[i] > min;
          }
          if (maxIncl) {
            accept &= values[i] <= max;
          } else {
            accept &= values[i] < max;
          }
          if (accept) {
            expectedCounts[rangeID]++;
            minAcceptedValue = Math.min(minAcceptedValue, values[i]);
            maxAcceptedValue = Math.max(maxAcceptedValue, values[i]);
          }
        }
      }

      FacetsCollector sfc = new FacetsCollector();
      s.search(new MatchAllDocsQuery(), sfc);
      Filter fastMatchFilter;
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchFilter = fieldTypes.newRangeFilter("field", minValue, true, maxValue, true);
        } else {
          fastMatchFilter = fieldTypes.newRangeFilter("field", minAcceptedValue, true, maxAcceptedValue, true);
        }
      } else {
        fastMatchFilter = null;
      }
      ValueSource vs = new DoubleFieldSource("field");
      Facets facets = new DoubleRangeFacetCounts("field", vs, sfc, fastMatchFilter, ranges);
      FacetResult result = facets.getTopChildren(10, "field");
      assertEquals(numRange, result.labelValues.length);
      for(int rangeID=0;rangeID<numRange;rangeID++) {
        if (VERBOSE) {
          System.out.println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID]);
        }
        LabelAndValue subNode = result.labelValues[rangeID];
        assertEquals("r" + rangeID, subNode.label);
        assertEquals(expectedCounts[rangeID], subNode.value.intValue());

        DoubleRange range = ranges[rangeID];

        // Test drill-down:
        DrillDownQuery ddq = new DrillDownQuery(config);
        if (random().nextBoolean()) {
          if (random().nextBoolean()) {
            ddq.add("field", fieldTypes.newRangeFilter("field", range.min, range.minInclusive, range.max, range.maxInclusive));
          } else {
            ddq.add("field", new ConstantScoreQuery(fieldTypes.newRangeFilter("field", range.min, range.minInclusive, range.max, range.maxInclusive)));
          }
        } else {
          ddq.add("field", range.getFilter(fastMatchFilter, vs));
        }

        assertEquals(expectedCounts[rangeID], s.search(ddq, 10).totalHits);
      }
    }

    w.close();
    IOUtils.close(r, dir);
  }

  // LUCENE-5178
  public void testMissingValues() throws Exception {
    Directory d = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), d);
    for(long l=0;l<100;l++) {
      // Every 5th doc is missing the value:
      if (l % 5 == 0) {
        w.addDocument(w.newDocument());
        continue;
      }
      Document doc = w.newDocument();
      doc.addLong("field", l);
      w.addDocument(doc);
    }

    IndexReader r = w.getReader();

    FacetsCollector fc = new FacetsCollector();

    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);
    Facets facets = new LongRangeFacetCounts("field", fc,
        new LongRange("less than 10", 0L, true, 10L, false),
        new LongRange("less than or equal to 10", 0L, true, 10L, true),
        new LongRange("over 90", 90L, false, 100L, false),
        new LongRange("90 or above", 90L, true, 100L, false),
        new LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false));
    
    assertEquals("dim=field path=[] value=16 childCount=5\n  less than 10 (8)\n  less than or equal to 10 (8)\n  over 90 (8)\n  90 or above (8)\n  over 1000 (0)\n",
                 facets.getTopChildren(10, "field").toString());

    w.close();
    IOUtils.close(r, d);
  }

  public void testCustomDoublesValueSource() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    
    Document doc = writer.newDocument();
    writer.addDocument(doc);
    writer.addDocument(doc);
    writer.addDocument(doc);

    // Test wants 3 docs in one segment:
    writer.forceMerge(1);

    final ValueSource vs = new ValueSource() {
        @SuppressWarnings("rawtypes")
        @Override
        public FunctionValues getValues(Map ignored, LeafReaderContext ignored2) {
          return new DoubleDocValues(null) {
            @Override
            public double doubleVal(int doc) {
              return doc+1;
            }
          };
        }

        @Override
        public boolean equals(Object o) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
          throw new UnsupportedOperationException();
        }

        @Override
        public String description() {
          throw new UnsupportedOperationException();
        }
      };

    FacetsConfig config = new FacetsConfig();
    
    FacetsCollector fc = new FacetsCollector();

    IndexReader r = writer.getReader();
    IndexSearcher s = newSearcher(r);
    s.search(new MatchAllDocsQuery(), fc);

    final DoubleRange[] ranges = new DoubleRange[] {
        new DoubleRange("< 1", 0.0, true, 1.0, false),
        new DoubleRange("< 2", 0.0, true, 2.0, false),
        new DoubleRange("< 5", 0.0, true, 5.0, false),
        new DoubleRange("< 10", 0.0, true, 10.0, false),
        new DoubleRange("< 20", 0.0, true, 20.0, false),
        new DoubleRange("< 50", 0.0, true, 50.0, false)};

    final Filter fastMatchFilter;
    final AtomicBoolean filterWasUsed = new AtomicBoolean();
    if (random().nextBoolean()) {
      // Sort of silly:
      fastMatchFilter = new CachingWrapperFilter(new QueryWrapperFilter(new MatchAllDocsQuery()), FilterCachingPolicy.ALWAYS_CACHE) {
          @Override
          protected DocIdSet cacheImpl(DocIdSetIterator iterator, LeafReader reader)
            throws IOException {
            final FixedBitSet cached = new FixedBitSet(reader.maxDoc());
            filterWasUsed.set(true);
            cached.or(iterator);
            return new BitDocIdSet(cached);
          }
        };
    } else {
      fastMatchFilter = null;
    }

    if (VERBOSE) {
      System.out.println("TEST: fastMatchFilter=" + fastMatchFilter);
    }

    Facets facets = new DoubleRangeFacetCounts("field", vs, fc, fastMatchFilter, ranges);

    assertEquals("dim=field path=[] value=3 childCount=6\n  < 1 (0)\n  < 2 (1)\n  < 5 (3)\n  < 10 (3)\n  < 20 (3)\n  < 50 (3)\n", facets.getTopChildren(10, "field").toString());
    assertTrue(fastMatchFilter == null || filterWasUsed.get());

    DrillDownQuery ddq = new DrillDownQuery(config);
    ddq.add("field", ranges[1].getFilter(fastMatchFilter, vs));

    // Test simple drill-down:
    assertEquals(1, s.search(ddq, 10).totalHits);

    // Test drill-sideways after drill-down
    DrillSideways ds = new DrillSideways(s, config, (TaxonomyReader) null) {

        @Override
        protected Facets buildFacetsResult(FacetsCollector drillDowns, FacetsCollector[] drillSideways, String[] drillSidewaysDims) throws IOException {        
          assert drillSideways.length == 1;
          return new DoubleRangeFacetCounts("field", vs, drillSideways[0], fastMatchFilter, ranges);
        }

        @Override
        protected boolean scoreSubDocsAtOnce() {
          return random().nextBoolean();
        }
      };


    DrillSidewaysResult dsr = ds.search(ddq, 10);
    assertEquals(1, dsr.hits.totalHits);
    assertEquals("dim=field path=[] value=3 childCount=6\n  < 1 (0)\n  < 2 (1)\n  < 5 (3)\n  < 10 (3)\n  < 20 (3)\n  < 50 (3)\n",
                 dsr.facets.getTopChildren(10, "field").toString());

    writer.close();
    IOUtils.close(r, dir);
  }
}
