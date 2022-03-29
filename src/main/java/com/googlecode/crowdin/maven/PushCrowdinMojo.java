package com.googlecode.crowdin.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jdom2.Document;
import org.jdom2.Element;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

/**
 * Push Maven translations of this project in crowdin
 */
@Mojo(name = "push", threadSafe = true)
public class PushCrowdinMojo extends AbstractCrowdinMojo {

    private void crowdinCreateFolder(String folderName) throws MojoExecutionException {
        getLog().info("Creating " + folderName + " folder on crowdin");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("name", folderName);
        crowdinRequestAPI("add-directory", parameters, null, true);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        if (messagesInputDirectory.exists()) {

            Map<String, File> newFiles = new HashMap<>();
            Map<String, File> toUpdateFiles = new HashMap<>();
            List<String> toDeleteFiles = new ArrayList<>();

            // Retrieve project informations
            getLog().info("Retrieving project informations");
            Document projectDetails = crowdinRequestAPI("info", null, null, false);

            // Get crowdin files
            Element filesElement = projectDetails.getRootElement().getChild("files");
            String folderName = getMavenId(project.getArtifact());

            // Get Maven files
            getLog().debug("Retrieving message files from this project");
            Map<String, File> files = getMessageFiles(folderName);

            if (!crowdinContainsFile(filesElement, folderName, true)) {
                // Create project folder if it does not exist
                crowdinCreateFolder(folderName);
            } else {
                // List crowdin files
                List<Element> items = filesElement.getChildren("item");
                Element projectFolder = crowdinGetFolder(items, folderName);
                Element subFiles = projectFolder.getChild("files");
                List<Element> subItems = subFiles.getChildren("item");
                for (Element subItem : subItems) {
                    if (!crowdinIsFolder(subItem)) {
                        // get crowdin name
                        String name = subItem.getChildTextNormalize("name");
                        // check that files still exist
                        String mapName = folderName + "/" + name;
                        if (!files.containsKey(mapName)) {
                            getLog().debug(mapName + " is in crowdin project but not in this project, delete it later");
                            // otherwise delete it from crowdin
                            toDeleteFiles.add(mapName);
                        }
                    }
                }
            }

            // For existing maven files, check if file exist or not on crowdin
            Set<Entry<String, File>> entrySet = files.entrySet();
            for (Entry<String, File> entry : entrySet) {
                if (crowdinContainsFile(filesElement, entry.getKey(), false)) {
                    // update
                    getLog().debug(entry.getKey() + " has to be updated");
                    toUpdateFiles.put(entry.getKey(), entry.getValue());
                } else {
                    // put
                    getLog().debug(entry.getKey() + " has to be added");
                    newFiles.put(entry.getKey(), entry.getValue());
                }
            }

            if (toUpdateFiles.size() != 0) {
                getLog().info("Updating files on crowdin :");
                for (String toUpdateFile : toUpdateFiles.keySet()) {
                    getLog().info(toUpdateFile);
                }
                crowdinRequestAPI("update-file", null, toUpdateFiles, true);
            }
            if (newFiles.size() != 0) {
                getLog().info("Adding files on crowdin :");
                for (String newFile : newFiles.keySet()) {
                    getLog().info(newFile);
                }
                Map<String, String> parameters = new HashMap<>();
                parameters.put("type", "properties");
                crowdinRequestAPI("add-file", parameters, newFiles, true);
            }
            for (String toDeleteFile : toDeleteFiles) {
                Map<String, String> parameters = new HashMap<>();
                parameters.put("file", toDeleteFile);
                getLog().info("Deleting " + toDeleteFile + " on crowdin");
                crowdinRequestAPI("delete-file", parameters, null, true);
            }

        } else {
            getLog().info(messagesInputDirectory.getPath() + " not found");
        }
    }

    private Map<String, File> getMessageFiles(String folderName) {
        Map<String, File> result = new HashMap<>();
        File[] listFiles = messagesInputDirectory.listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (!file.isDirectory() && !file.getName().startsWith(".") && file.getName().endsWith(".properties")) {
                    String crowdinPath = folderName + "/" + file.getName();
                    getLog().debug("Found " + crowdinPath);
                    result.put(crowdinPath, file);
                }
            }
        }
        return result;
    }

}
