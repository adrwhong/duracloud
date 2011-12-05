/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.durastore.util;

import org.duracloud.common.model.AclType;
import org.duracloud.security.context.SecurityContextUtil;
import org.duracloud.security.error.NoUserLoggedInException;
import org.duracloud.security.impl.DuracloudUserDetails;
import org.duracloud.storage.provider.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.GrantedAuthority;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class provides a filtering of spaces based on the username and groups
 * of the currently logged-in user. Additionally, caching of space ACLs and
 * access-type (opened/closed) is performed in this class.
 *
 * @author Andrew Woods
 *         Date: 11/22/11
 */
public class ACLStorageProvider implements StorageProvider {

    private final Logger log =
        LoggerFactory.getLogger(ACLStorageProvider.class);

    private final StorageProvider targetProvider;
    private SecurityContextUtil securityContextUtil;

    private Map<String, AccessType> spaceAccessMap;
    private Map<String, Map<String, AclType>> spaceACLMap;
    private boolean loaded;

    public ACLStorageProvider(StorageProvider targetProvider) {
        this(targetProvider, new SecurityContextUtil());
    }

    public ACLStorageProvider(StorageProvider targetProvider,
                              SecurityContextUtil securityContextUtil) {
        this.targetProvider = targetProvider;
        this.securityContextUtil = securityContextUtil;
        this.spaceAccessMap = new HashMap<String, AccessType>();
        this.spaceACLMap = new HashMap<String, Map<String, AclType>>();
        this.loaded = false;

        new Thread(new CacheLoader()).start();
    }

    /**
     * This nested class loads the cache of space ACLs and AccessTypes.
     */
    private class CacheLoader implements Runnable {
        public void run() {
            Iterator<String> spaces = targetProvider.getSpaces();
            while (spaces.hasNext()) {
                String space = spaces.next();
                spaceAccessMap.put(space, targetProvider.getSpaceAccess(space));
                spaceACLMap.put(space, targetProvider.getSpaceACLs(space));
            }
            loaded = true;
        }
    }

    private void waitForCache() {
        while (!loaded) {
            log.debug("waiting: {}", targetProvider.getClass().getName());
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    /**
     * This method passes through the call to getSpaces() for 'admin' users,
     * and for 'users', only returns the list of 'open' spaces and those to
     * which they have and ACL.
     *
     * @return spaces to which the user has read permissions
     */
    @Override
    public Iterator<String> getSpaces() {
        DuracloudUserDetails user = getCurrentUserDetails();
        if (isAdmin(user)) {
            return targetProvider.getSpaces();
        }

        waitForCache();
        List<String> spaces = new ArrayList<String>();

        for (String space : spaceAccessMap.keySet()) {
            if (AccessType.OPEN.equals(spaceAccessMap.get(space))) {
                spaces.add(space);
            }
        }

        for (String space : spaceACLMap.keySet()) {
            Map<String, AclType> acls = spaceACLMap.get(space);
            if (userHasAccess(user, acls) && !spaces.contains(space)) {
                spaces.add(space);
            }
        }

        Collections.sort(spaces);
        return spaces.iterator();
    }

    private boolean isAdmin(DuracloudUserDetails user) {
        if (null == user) {
            return false;
        }

        GrantedAuthority[] auths = user.getAuthorities();
        if (null != auths) {
            for (GrantedAuthority auth : auths) {
                if ("ROLE_ADMIN".equals(auth.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    private DuracloudUserDetails getCurrentUserDetails() {
        try {
            return securityContextUtil.getCurrentUserDetails();
        } catch (NoUserLoggedInException e) {
            return null;
        }
    }

    private boolean userHasAccess(DuracloudUserDetails user,
                                  Map<String, AclType> acls) {
        if (acls.keySet().contains(PROPERTIES_SPACE_ACL + user.getUsername())) {
            return true;
        }

        List<String> groups = user.getGroups();
        if (null == groups || groups.size() == 0) {
            return false;
        }

        for (String group : groups) {
            if (acls.keySet().contains(PROPERTIES_SPACE_ACL + group)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<String> getSpaceContents(String spaceId, String prefix) {
        return targetProvider.getSpaceContents(spaceId, prefix);
    }

    @Override
    public List<String> getSpaceContentsChunked(String spaceId,
                                                String prefix,
                                                long maxResults,
                                                String marker) {
        return targetProvider.getSpaceContentsChunked(spaceId,
                                                      prefix,
                                                      maxResults,
                                                      marker);
    }

    @Override
    public void createSpace(String spaceId) {
        waitForCache();

        // update the cache to contain current user privileges for new space
        DuracloudUserDetails userDetails = getCurrentUserDetails();
        if (null != userDetails) {
            Map<String, AclType> acl = new HashMap<String, AclType>();
            acl.put(PROPERTIES_SPACE_ACL + userDetails.getUsername(),
                    AclType.WRITE);
            spaceACLMap.put(spaceId, acl);
        }

        targetProvider.createSpace(spaceId);
    }

    @Override
    public void deleteSpace(String spaceId) {
        waitForCache();

        spaceAccessMap.remove(spaceId);
        spaceACLMap.remove(spaceId);

        targetProvider.deleteSpace(spaceId);
    }

    @Override
    public Map<String, String> getSpaceProperties(String spaceId) {
        return targetProvider.getSpaceProperties(spaceId);
    }

    @Override
    public void setSpaceProperties(String spaceId,
                                   Map<String, String> spaceProperties) {
        waitForCache();

        // cache new space properties
        if (null != spaceProperties) {
            // update cache
            String access = spaceProperties.get(PROPERTIES_SPACE_ACCESS);
            if (null != access) {
                try {
                    AccessType accessType = AccessType.valueOf(access);
                    spaceAccessMap.put(spaceId, accessType);

                } catch (RuntimeException e) {
                    // do nothing
                }
            }
        }

        targetProvider.setSpaceProperties(spaceId, spaceProperties);
    }

    @Override
    public Map<String, AclType> getSpaceACLs(String spaceId) {
        DuracloudUserDetails user = getCurrentUserDetails();
        if (isAdmin(user) && !loaded) {
            return targetProvider.getSpaceACLs(spaceId);
        }

        waitForCache();

        if (spaceACLMap.containsKey(spaceId)) {
            return spaceACLMap.get(spaceId);

        } else {
            Map<String, AclType> acls = targetProvider.getSpaceACLs(spaceId);
            spaceACLMap.put(spaceId, acls);
            return acls;
        }
    }

    @Override
    public void setSpaceACLs(String spaceId, Map<String, AclType> spaceACLs) {
        waitForCache();

        if (null != spaceACLs) {
            // update cache
            this.spaceACLMap.put(spaceId, spaceACLs);
        }

        targetProvider.setSpaceACLs(spaceId, spaceACLs);
    }

    @Override
    public AccessType getSpaceAccess(String spaceId) {
        DuracloudUserDetails user = getCurrentUserDetails();
        if (isAdmin(user) && !loaded) {
            return targetProvider.getSpaceAccess(spaceId);
        }

        waitForCache();

        if (spaceAccessMap.containsKey(spaceId)) {
            return spaceAccessMap.get(spaceId);

        } else {
            AccessType access = targetProvider.getSpaceAccess(spaceId);
            spaceAccessMap.put(spaceId, access);
            return access;
        }
    }

    @Override
    public void setSpaceAccess(String spaceId, AccessType access) {
        waitForCache();

        if (null != access) {
            // update cache
            this.spaceAccessMap.put(spaceId, access);
        }

        targetProvider.setSpaceAccess(spaceId, access);
    }

    @Override
    public String addContent(String spaceId,
                             String contentId,
                             String contentMimeType,
                             Map<String, String> userProperties,
                             long contentSize,
                             String contentChecksum,
                             InputStream content) {
        return targetProvider.addContent(spaceId,
                                         contentId,
                                         contentMimeType,
                                         userProperties,
                                         contentSize,
                                         contentChecksum,
                                         content);
    }

    @Override
    public String copyContent(String sourceSpaceId,
                              String sourceContentId,
                              String destSpaceId,
                              String destContentId) {
        return targetProvider.copyContent(sourceSpaceId,
                                          sourceContentId,
                                          destSpaceId,
                                          destContentId);
    }

    @Override
    public InputStream getContent(String spaceId, String contentId) {
        return targetProvider.getContent(spaceId, contentId);
    }

    @Override
    public void deleteContent(String spaceId, String contentId) {
        targetProvider.deleteContent(spaceId, contentId);
    }

    @Override
    public void setContentProperties(String spaceId,
                                     String contentId,
                                     Map<String, String> contentProperties) {
        targetProvider.setContentProperties(spaceId,
                                            contentId,
                                            contentProperties);
    }

    @Override
    public Map<String, String> getContentProperties(String spaceId,
                                                    String contentId) {
        return targetProvider.getContentProperties(spaceId, contentId);
    }
}
