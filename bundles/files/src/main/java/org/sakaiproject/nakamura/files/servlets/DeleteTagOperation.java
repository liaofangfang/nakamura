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
package org.sakaiproject.nakamura.files.servlets;

import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_UUIDS;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.servlets.post.Modification;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.files.FileUtils;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(name = "DeleteTagOperation", shortDescription = "Delete a tag from node", description = { "Delete a tag from a node." }, methods = { @ServiceMethod(name = "POST", description = { "This operation should be performed on the node you wish to tag. Tagging on any item will be performed by adding a weak reference to the content item. Put simply a sakai:tag-uuid property with the UUID of the tag node. We use the UUID to uniquely identify the tag in question, a string of the tag name is not sufficient. This allows the tag to be renamed and moved without breaking the relationship. Additionally for convenience purposes we may put the name of the tag at the time of tagging in sakai:tag although this will not be actively maintained. " }, parameters = {
    @ServiceParameter(name = ":operation", description = "The value HAS TO BE <i>tag</i>."),
    @ServiceParameter(name = "key", description = "Can be either 1) A fully qualified path, 2) UUID, or 3) a content poolId.") }, response = {
    @ServiceResponse(code = 201, description = "The tag was added to the node."),
    @ServiceResponse(code = 400, description = "The request did not have sufficient information to perform the tagging, probably a missing parameter or the uuid does not point to an existing tag."),
    @ServiceResponse(code = 403, description = "Anonymous users can't tag anything, other people can tag <i>every</i> node in the repository where they have READ on."),
    @ServiceResponse(code = HttpServletResponse.SC_NOT_FOUND, description = "Requested Node  for given key could not be found."),
    @ServiceResponse(code = 500, description = "Something went wrong, the error is in the HTML.") }) }, bindings = { @ServiceBinding(type = BindingType.OPERATION, bindings = { "tag" }) })
@Component(immediate = true)
@Service(value = SparsePostOperation.class)
@Properties(value = {
    @Property(name = "sling.post.operation", value = "deletetag"),
    @Property(name = "service.description", value = "Creates an internal link to a file."),
    @Property(name = "service.vendor", value = "The Sakai Foundation") })
public class DeleteTagOperation extends AbstractSparsePostOperation {

  @Reference
  protected transient SlingRepository slingRepository;

  @Reference
  protected transient EventAdmin eventAdmin;

  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteTagOperation.class);

  private static final long serialVersionUID = -7724827744698056843L;

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.servlets.post.AbstractSlingPostOperation#doRun(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.servlets.HtmlResponse, java.util.List)
   */
  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath) throws StorageClientException, AccessDeniedException {

    // Check if the user has the required minimum privilege.
    String user = request.getRemoteUser();
    if (UserConstants.ANON_USERID.equals(user)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN,
          "Anonymous users can't tag things.");
      return;
    }

    // Check if the uuid is in the request.
    RequestParameter key = request.getRequestParameter("key");
    if (key == null || "".equals(key.getString())) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
          "Missing parameter: key");
      return;
    }

    ResourceResolver resourceResolver = request.getResourceResolver();
    Node tagNode = null;
    Resource resource = request.getResource();
    Node node = resource.adaptTo(Node.class);
    Content content = resource.adaptTo(Content.class);

    if (node == null && content == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
          "A tag operation must be performed on an actual resource");
      return;
    }


    // Grab the tagNode.
    try {
      tagNode = FileUtils.resolveNode(key.getString(), resourceResolver);
      if (tagNode == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND, "Provided key not found.");
        return;
      }
      if (!FileUtils.isTag(tagNode)) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST,
            "Provided key doesn't point to a tag.");
        return;
      }
    } catch (RepositoryException e1) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST, "Could not locate the tag.");
      return;
    }

    try {
      String uuid = tagNode.getIdentifier();
      // We check if the node already has this tag.
      // If it does, we ignore it..
      if (node != null && hasUuid(node, uuid)) {
        Session adminSession = null;
        try {
          adminSession = slingRepository.loginAdministrative(null);

          LOGGER.info("Delete tagging [{}] from  [{}] [{}] ",
              new Object[] { tagNode, node, uuid });
          // Add the tag on the file.
          FileUtils.deleteTag(adminSession, node, tagNode);

          // Save our modifications.
          if (adminSession.hasPendingChanges()) {
            adminSession.save();
          }
        } finally {
          adminSession.logout();
        }
      } else if ( content != null ) {
        FileUtils.deleteTag(contentManager, content, tagNode);
      }

    } catch (RepositoryException e) {
      LOGGER.error("Failed to Delete Tag ",e);
      response.setStatus(500, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER.error("Failed to Delete Tag ",e);
      response.setStatus(500, e.getMessage());
    } catch (StorageClientException e) {
      LOGGER.error("Failed to Delete Tag ",e);
      response.setStatus(500, e.getMessage());
    }

  }

  /**
   * Checks if the node already has the uuid in it's properties.
   *
   * @param node
   * @param uuid
   * @return
   * @throws RepositoryException
   */
  protected boolean hasUuid(Node node, String uuid) throws RepositoryException {
    Value[] uuids = JcrUtils.getValues(node, SAKAI_TAG_UUIDS);
    for (Value v : uuids) {
      if (v.getString().equals(uuid)) {
        return true;
      }
    }
    return false;
  }
}
