/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.search.solr;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.ResourceJsonWriter;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Formats user profile node search results
 * 
 */

@Component(immediate = true, metatype = true)
@Properties(value = { @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_PROCESSOR_NAMES, value = "Resource"),
    @Property(name = SolrSearchResultProcessor.DEFAULT_PROCESSOR_PROP, boolValue = true) })
@Service
public class DefaultSearchResultProcessor implements SolrSearchResultProcessor {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(DefaultSearchBatchResultProcessor.class);
  @Reference
  private SolrSearchServiceFactory searchServiceFactory;

  DefaultSearchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
    if (searchServiceFactory == null) {
      throw new IllegalArgumentException(
          "Search Service Factory must be set when not using as a component");
    }
    this.searchServiceFactory = searchServiceFactory;
  }

  public DefaultSearchResultProcessor() {
  }

  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request,
      String queryString) throws SolrSearchException {
    return searchServiceFactory.getSearchResultSet(request, queryString);
  }

  public void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result)
      throws JSONException {
    ExtendedJSONWriter.writeValueMap(write,result.getProperties());
  }
}
