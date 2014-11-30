package org.apache.lucene.search;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Test the automaton query for several unicode corner cases,
 * specifically enumerating strings/indexes containing supplementary characters,
 * and the differences between UTF-8/UTF-32 and UTF-16 binary sort order.
 */
public class TestAutomatonQueryUnicode extends LuceneTestCase {
  private IndexReader reader;
  private IndexSearcher searcher;
  private Directory directory;

  private final String FN = "field";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);

    for(String body : new String[] {
        "\uD866\uDF05abcdef",
        "\uD866\uDF06ghijkl",
        "\uFB94mnopqr", // this sorts before the previous two in UTF-8/UTF-32, but after in UTF-16!!!
        "\uFB95stuvwx", // this one too.
        "a\uFFFCbc",
        "a\uFFFDbc",
        "a\uFFFEbc",
        "a\uFB94bc",
        "bacadaba",
        "\uFFFD",
        "\uFFFD\uD866\uDF05",
        "\uFFFD\uFFFD" }) {

      Document doc = writer.newDocument();
      doc.addLargeText(FN, body);
      doc.addShortText("title", "some title");
      doc.addShortText("footer", "a footer");
      writer.addDocument(doc);
    }

    reader = writer.getReader();
    searcher = newSearcher(reader);
    writer.close();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    directory.close();
    super.tearDown();
  }

  private Term newTerm(String value) {
    return new Term(FN, value);
  }

  private int automatonQueryNrHits(AutomatonQuery query) throws IOException {
    return searcher.search(query, 5).totalHits;
  }

  private void assertAutomatonHits(int expected, Automaton automaton)
      throws IOException {
    AutomatonQuery query = new AutomatonQuery(newTerm("bogus"), automaton);

    query.setRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
    assertEquals(expected, automatonQueryNrHits(query));

    query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
    assertEquals(expected, automatonQueryNrHits(query));

    query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
    assertEquals(expected, automatonQueryNrHits(query));
  }

  /**
   * Test that AutomatonQuery interacts with lucene's sort order correctly.
   * 
   * This expression matches something either starting with the arabic
   * presentation forms block, or a supplementary character.
   */
  public void testSortOrder() throws IOException {
    Automaton a = new RegExp("((\uD866\uDF05)|\uFB94).*").toAutomaton();
    assertAutomatonHits(2, a);
  }
}
