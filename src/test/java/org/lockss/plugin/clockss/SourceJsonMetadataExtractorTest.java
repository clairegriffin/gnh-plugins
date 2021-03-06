/*
 * Copyright (c) 2019 Board of Trustees of Leland Stanford Jr. University,
 * all rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Stanford University shall not
 * be used in advertising or otherwise to promote the sale, use or other dealings
 * in this Software without prior written authorization from Stanford University.
 */

package org.lockss.plugin.clockss;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.FileMetadataListExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.SourceJsonMetadataExtractorFactory.SourceJsonMetadataExtractor;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockArchivalUnit;
import org.lockss.test.MockCachedUrl;
import org.lockss.test.MockLockssDaemon;
import org.lockss.util.CIProperties;
import org.lockss.util.Logger;

/*
 *  Set up useful utility methods to help with extractors based on
 *  SourceJsonMetadataExtractor
 *  The class
 *    fooSourceJsonMetadataExtractor which extends sourcejsonMetadataExtractor
 *  should define a test class
 *    testFooSourcejsonMDExtractor which extends testSourcejsonMetadataExtractor
 *  it will then have access to the utility functions for setting up json
 *  source and examining the results.
 *  Additionally, the test class can define a test version of the extractor
 *  that does not require validating against actual content files in order
 *  to simplify testing.
 */
public class SourceJsonMetadataExtractorTest
    extends LockssTestCase {

  // doesn't matter. just not empty
  private static final String DEF_PDF_CONTENT = "    ";
  private static Logger log = Logger.getLogger(SourceJsonMetadataExtractorTest.class);
  private static String DEFAULT_PLUGIN_NAME = "org.lockss.plugin.clockss.ClockssSourcePlugin";
  private static String DEFAULT_BASE_URL = "http://www.source.org/";
  private static String DEFAULT_YEAR = "2013";
  private static String DEFAULT_JSON_URL = "";
  private static String DEFAULT_JSON_MIME = "application/json";
  private static String plugin_name;
  private static String base_url;
  private static String year;
  private static CIProperties pdfHeader;
  private MockLockssDaemon theDaemon;
  private MockArchivalUnit mau;


  public SourceJsonMetadataExtractorTest() {
    super();
    plugin_name = DEFAULT_PLUGIN_NAME;
    base_url = DEFAULT_BASE_URL;
    year = DEFAULT_YEAR;

    pdfHeader = new CIProperties();
    pdfHeader.put(CachedUrl.PROPERTY_CONTENT_TYPE, "application/pdf");
  }

  /*
   * alternate constructor allows for custom setting of plugin params
   * In most cases, default shuld work
   */
  public SourceJsonMetadataExtractorTest(
      String inPName,
      String inBase, String inYear) {
    super();
    plugin_name = inPName;
    base_url = inBase;
    year = inYear;
  }

  /*
   * For use in building up urls, get back the params being used
   *
   */
  public String getBaseUrl() {
    return base_url;
  }

  public String getYear() {
    return year;
  }


  public void setUp() throws Exception {
    super.setUp();
    setUpDiskSpace(); // you need this to have startService work properly...

    theDaemon = getMockLockssDaemon();
    mau = new MockArchivalUnit();

    theDaemon.getAlertManager();
    theDaemon.getPluginManager().setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();
    theDaemon.getCrawlManager();
    mau.setConfiguration(auConfig());
  }

  public void tearDown() throws Exception {
    theDaemon.stopDaemon();
    super.tearDown();
  }

  /**
   * Configuration method.
   */
  Configuration auConfig() {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("base_url", base_url);
    conf.put("year", year);
    return conf;
  }

  /*
   * USEFUL TEST METHODS
   */
  //TODO
  public String getContentFromFile(String inFileName) {
    return null;
  }

  /*
   * Take a string of content and use a pattern to match chunks to replace
   * using the ordered values in the list of String replacements.
   * Useful to say, take an json template, and fill in a set of DOIs - one per record.
   * If you wish to replace the DOIs and then the ISSNs with set values, just
   * call the method twice using different replacement patterns
   * Use with care - it will only replace as many instances as there are replacements
   */
  public String getContentFromTemplate(String template, Pattern replacePattern,
                                       List<String> replacements) {
    String content = template;
    for (String replacement : replacements) {
      Matcher mat = replacePattern.matcher(content);
      if (mat.find()) {
        content = mat.replaceFirst(replacement);
      }
    }
    return content;
  }

  /*
   * Take a string of content and use a pattern to match chunks to replace
   * all occurrences of the pattern with the replacement string.
   */
  public String getContentFromTemplate(String template, Pattern replacePattern,
                                       String replacement) {
    Matcher mat = replacePattern.matcher(template);
    if (mat.find()) {
      return mat.replaceAll(replacement);
    }
    return template;
  }

  /*
   * Ways to extract content
   *  - minimum: test specifies String content an FileMetadataListExtractor
   *  - optional - provide list of String pdf_urls to be added to the mock daemon
   *  - optional - provide a specific json_url and json_url_mime to be used for the content
   *  In each case the method returns an mdList containing the extracted metadata
   */
  public List<ArticleMetadata> extractFromContent(String inContent,
                                                  FileMetadataListExtractor mfle) {
    return extractFromContent(DEFAULT_JSON_URL, DEFAULT_JSON_MIME, inContent, mfle, null);
  }

  public List<ArticleMetadata> extractFromContent(String inContent,
                                                  FileMetadataListExtractor mfle,
                                                  List<String> pdf_urls) {
    return extractFromContent(DEFAULT_JSON_URL, DEFAULT_JSON_MIME, inContent, mfle, pdf_urls);
  }

  public List<ArticleMetadata> extractFromContent(String inUrl,
                                                  String inMime,
                                                  String inContent,
                                                  FileMetadataListExtractor mfle) {
    return extractFromContent(inUrl, inMime, inContent, mfle, null);
  }

  public List<ArticleMetadata> extractFromContent(String json_url,
                                                  String json_url_mime,
                                                  String inContent,
                                                  FileMetadataListExtractor mfle,
                                                  List<String> pdf_urls) {

    CIProperties jsonHead = new CIProperties();
    jsonHead.put(CachedUrl.PROPERTY_CONTENT_TYPE, json_url_mime);
    MockCachedUrl cu = mau.addUrl(json_url, true, true, jsonHead);
    cu.setContent(inContent);
    cu.setContentSize(inContent.length());
    //cu.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, json_url_mime);
    if (pdf_urls != null) {
      Iterator<String> pIt = pdf_urls.iterator();
      while (pIt.hasNext()) {
        String pdf_url = pIt.next();
        MockCachedUrl pdf_cu = mau.addUrl(pdf_url, true, true, pdfHeader);
        pdf_cu.setContent(DEF_PDF_CONTENT); // just not empty
        pdf_cu.setContentSize(DEF_PDF_CONTENT.length());
      }
    }
    try {
      return mfle.extract(MetadataTarget.Any(), cu);
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (PluginException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  /*
   *  Convenience methods to help with testing returned results
   *  For one metadata record (either the sole record in a list or an
   *  individual record), check the record content against the
   *  specified field and value OR
   *  against a map of fields and values.
   *  For extraction that returns multiple records, the testor should control
   *  iterating over the list to control individual record validation since
   *  the order of the returned list is arbitrary.
   */
  public void checkOneMD(List<ArticleMetadata> mdList, MetadataField checkField,
                         String checkValue) {
    assertEquals(1, mdList.size());
    checkOneMD(mdList.get(0), checkField, checkValue);
  }

  public void checkOneMD(ArticleMetadata md, MetadataField checkField,
                         String checkValue) {
    if (checkField.getCardinality() == MetadataField.Cardinality.Single) {
      assertEquals(checkValue, md.get(checkField));
    }
    else {
      // dealing with a multi
      assertEquals(checkValue, md.getList(checkField).toString());
    }
  }

  public void checkOneMD(List<ArticleMetadata> mdList, Map<MetadataField, String> checkMap) {
    assertEquals(1, mdList.size());
    checkOneMD(mdList.get(0), checkMap);
  }

  public void checkOneMD(ArticleMetadata md, Map<MetadataField, String> checkMap) {

    log.debug3("checkOneMD: " + md.ppString(2));
    if (checkMap != null) {
      //Iterate over the map
      for (MetadataField keyField : checkMap.keySet()) {
        // must do special for multi value
        if (keyField.getCardinality() == MetadataField.Cardinality.Single) {
          assertEquals(checkMap.get(keyField), md.get(keyField));
        }
        else {
          // dealing with a multi
          assertEquals(checkMap.get(keyField), md.getList(keyField).toString());
        }
      }
    }
  }


  /*
   * Printout functions, useful for debugging;
   * list or individual record
   * raw map, cooked map or both
   */
  public void debug3_MDList(List<ArticleMetadata> mdList) {
    Iterator<ArticleMetadata> mdIt = mdList.iterator();
    ArticleMetadata mdRecord = null;
    while (mdIt.hasNext()) {
      mdRecord = mdIt.next();
      debug3_MDRecord(mdRecord);
    }
  }

  public void debug3_MDRecord(ArticleMetadata md) {
    assertNotNull(md);
    log.debug3(md.ppString(2));
  }

}
