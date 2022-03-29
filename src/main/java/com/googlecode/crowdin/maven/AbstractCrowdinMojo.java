package com.googlecode.crowdin.maven;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public abstract class AbstractCrowdinMojo extends AbstractMojo {

    protected static final SAXBuilder saxBuilder = new SAXBuilder();

    /**
     * The current Maven project
     */
    @Parameter(readonly = true, required = true)
    protected MavenProject project;

    /**
     * The Maven Wagon manager to use when obtaining server authentication details.
     */
    @Component
    protected WagonManager wagonManager;

    /**
     * Server id in settings.xml. username is project identifier, password is API key
     */
    @Parameter(property = "crowdinServerId", required = true)
    protected String crowdinServerId;

    /**
     * The directory where the messages can be fund.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/messages", required = true)
    protected File messagesInputDirectory;

    /**
     * The directory where the messages can be fund.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/crowdin", required = true)
    protected File messagesOutputDirectory;

    protected CloseableHttpClient client;
    protected AuthenticationInfo authenticationInfo;

    public void execute() throws MojoExecutionException, MojoFailureException {
        authenticationInfo = wagonManager.getAuthenticationInfo(crowdinServerId);
        if (authenticationInfo == null) {
            throw new MojoExecutionException("Failed to find server with id " + crowdinServerId
                    + " in Maven settings (~/.m2/settings.xml)");
        }
        client = HttpClientBuilder.create().useSystemProperties().build();
    }

    protected boolean crowdinContainsFile(Element files, String fileName, boolean folder) {
        getLog().debug("Check that crowdin project contains " + fileName);
        List<Element> items = files.getChildren("item");
        int slash = fileName.indexOf('/');
        if (slash == -1) {
            if (folder) {
                Element folderElement = crowdinGetFolder(items, fileName);
                if (folderElement != null) {
                    getLog().debug("Crowdin project contains " + fileName);
                    return true;
                }
            } else {
                for (Element item : items) {
                    if (fileName.equals(item.getChildTextNormalize("name"))) {
                        getLog().debug("Crowdin project contains " + fileName);
                        return true;
                    }
                }
            }
        } else {
            String folderName = fileName.substring(0, slash);
            String subPath = fileName.substring(slash + 1);
            Element folderElement = crowdinGetFolder(items, folderName);
            if (folderElement != null) {
                Element subFiles = folderElement.getChild("files");
                return crowdinContainsFile(subFiles, subPath, folder);
            }
        }
        getLog().debug("Crowdin project does not contain " + fileName);
        return false;
    }

    protected Element crowdinGetFolder(List<Element> items, String fileName) {
        for (Element item : items) {
            if (fileName.equals(item.getChildTextNormalize("name"))) {
                if (crowdinIsFolder(item)) {
                    return item;
                }
            }
        }
        return null;
    }

    protected boolean crowdinIsFolder(Element item) {
        return item.getChild("node_type") != null && "directory".equals(item.getChildTextNormalize("node_type"));
    }

    protected Document crowdinRequestAPI(String method, Map<String, String> parameters, Map<String, File> files,
                                         boolean shallSuccess) throws MojoExecutionException {
        try {
            String uri = "http://api.crowdin.net/api/project/" + authenticationInfo.getUserName() + "/" + method
                    + "?key=" + authenticationInfo.getPassword();
            getLog().debug("Calling " + uri);
            HttpPost postMethod = new HttpPost(uri);

            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();

            if (parameters != null) {
                Set<Entry<String, String>> entrySetParameters = parameters.entrySet();
                for (Entry<String, String> entryParameter : entrySetParameters) {
                    multipartEntityBuilder.addPart(entryParameter.getKey(), new StringBody(entryParameter.getValue(), ContentType.TEXT_PLAIN));
                }
            }
            if (files != null) {
                Set<Entry<String, File>> entrySetFiles = files.entrySet();
                for (Entry<String, File> entryFile : entrySetFiles) {
                    String key = "files[" + entryFile.getKey() + "]";
                    multipartEntityBuilder.addPart(key, new FileBody(entryFile.getValue()));
                }
            }

            postMethod.setEntity(multipartEntityBuilder.build());

            // getLog().debug("Sent request : ");
            // ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // reqEntity.writeTo(bos);
            // getLog().debug(bos.toString());

            HttpResponse response = client.execute(postMethod);
            int returnCode = response.getStatusLine().getStatusCode();
            getLog().debug("Return code : " + returnCode);
            InputStream responseBodyAsStream = response.getEntity().getContent();
            Document document = saxBuilder.build(responseBodyAsStream);
            if (shallSuccess && document.getRootElement().getName().equals("error")) {
                String code = document.getRootElement().getChildTextNormalize("code");
                String message = document.getRootElement().getChildTextNormalize("message");
                throw new MojoExecutionException("Failed to call API - " + code + " - " + message);
            }
            return document;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to call API", e);
        }
    }

    protected String getMavenId(Artifact artifact) {
        return artifact.getGroupId() + "." + artifact.getArtifactId();
    }

}
