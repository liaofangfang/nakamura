package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.NodeNameGenerator;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparseCreateOperation;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.resource.lite.servlet.post.SparseCreateServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

public class ImportOperation extends AbstractSparseCreateOperation {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportOperation.class);

  public ImportOperation(NodeNameGenerator defaultNodeNameGenerator) {
    super(defaultNodeNameGenerator);
  }

  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, final List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException, IOException {


    Resource resource = request.getResource();
    String path = null;
    {
      Content content = resource.adaptTo(Content.class);
      if (content != null) {
        path = content.getPath();
      } else {
        // for some reason, if the operation posts to a path that does not exist and the
        // operation is create tree the SparseCreateServlet.doPost() operation is
        // bypassed. This is an ugly fix that works wound that problem. I suspect
        // somethings up in Sling.
        path = (String) request
            .getAttribute(SparseCreateServlet.CONTENT_TARGET_PATH_ATTRIBUTE);
      }
    }
    if (path == null) {
      throw new StorageClientException("No Path suppleid to create json tree at");
    }

    String contentType = request.getParameter(SlingPostConstants.RP_CONTENT_TYPE);
    if (contentType == null) {
      response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
          "Required :contentType parameter is missing");
      return;
    }

    // import options passed as request parameters.
    final boolean replace = "true".equalsIgnoreCase(request
        .getParameter(SlingPostConstants.RP_REPLACE));
    final boolean replaceProperties = "true".equalsIgnoreCase(request
        .getParameter(SlingPostConstants.RP_REPLACE_PROPERTIES));
    final boolean removeTree = "true".equalsIgnoreCase(request
        .getParameter(":removeTree"));

    String basePath = getItemPath(request);
    if (basePath.endsWith("/")) {
      // remove the trailing slash
      basePath = basePath.substring(0, basePath.length() - 1);
    }

    response.setCreateRequest(true);

    try {
      InputStream contentStream = null;
      String content = request.getParameter(SlingPostConstants.RP_CONTENT);
      if (content == null) {
        RequestParameter contentFile = request
            .getRequestParameter(SlingPostConstants.RP_CONTENT_FILE);
        if (contentFile != null) {
          contentStream = contentFile.getInputStream();
          content = IOUtils.toString(contentStream, "UTF-8");
        }
      }

      if (content == null) {
        response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
            "Missing content for import");
        return;
      } else {
        
        JSONObject json = new JSONObject(content);
        if ( LOGGER.isDebugEnabled() ) {
          LOGGER.debug("to {} importing {} ",basePath,json.toString(3));
        }
        LiteJsonImporter simpleJsonImporter = new LiteJsonImporter();
        simpleJsonImporter.importContent(contentManager, json, basePath, replace, replaceProperties, removeTree);
          response.setLocation(externalizePath(request, basePath));
          response.setPath(basePath);
          int lastSlashIndex = basePath.lastIndexOf('/');
          if (lastSlashIndex != -1) {
            String parentPath = basePath.substring(0, lastSlashIndex);
            response.setParentLocation(externalizePath(request, parentPath));
          }
      }

    } catch (IOException e) {
      LOGGER.error(e.getMessage(),e);
      throw new StorageClientException(e.getMessage(),e);
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(),e);
      throw new StorageClientException(e.getMessage(),e);
    }
  }

}
