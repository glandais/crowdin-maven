package com.googlecode.crowdin.maven;

import com.googlecode.crowdin.maven.tool.SortedProperties;
import com.googlecode.crowdin.maven.tool.SpecialArtifact;
import com.googlecode.crowdin.maven.tool.TranslationFile;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pull crowdin translations in this project, looking dependencies
 */
@Mojo(name = "pull", threadSafe = true)
public class PullCrowdinMojo extends AbstractCrowdinMojo {

    @Component
    protected DependencyGraphBuilder dependencyGraphBuilder;

    @Parameter
    protected MavenSession session;

    private void cleanFolders(Set<TranslationFile> translationFiles) {
        if (messagesOutputDirectory.exists()) {
            File[] languageFolders = messagesOutputDirectory.listFiles();
            if (languageFolders != null) {
                for (File languageFolder : languageFolders) {
                    if (!languageFolder.getName().startsWith(".") && languageFolder.isDirectory()) {
                        if (!containsLanguage(translationFiles, languageFolder.getName())) {
                            deleteFolder(languageFolder, true);
                        } else {
                            cleanLanguageFolder(languageFolder, translationFiles);
                        }
                    }
                }
            }
        }
    }

    private void cleanLanguageFolder(File languageFolder, Set<TranslationFile> translationFiles) {
        File[] mavenIds = languageFolder.listFiles();
        if (mavenIds != null) {
            for (File mavenId : mavenIds) {
                if (!mavenId.getName().startsWith(".") && mavenId.isDirectory()) {
                    boolean deleteRoot = !containsMavenId(translationFiles, mavenId.getName());
                    deleteFolder(mavenId, deleteRoot);
                }
            }
        }
    }

    private boolean containsLanguage(Set<TranslationFile> translationFiles, String language) {
        for (TranslationFile translationFile : translationFiles) {
            if (translationFile.getLanguage().equals(language)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMavenId(Set<TranslationFile> translationFiles, String mavenId) {
        for (TranslationFile translationFile : translationFiles) {
            if (translationFile.getMavenId().equals(mavenId)) {
                return true;
            }
        }
        return false;
    }

    private boolean deleteFolder(File folder, boolean deleteRoot) {
        File[] listFiles = folder.listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (!file.getName().startsWith(".") || deleteRoot) {
                    if (file.isDirectory()) {
                        deleteFolder(file, true);
                    }
                    if (!file.delete()) {
                        return false;
                    }
                    getLog().debug("Deleted " + file);
                }
            }
        }
        if (deleteRoot) {
            boolean deleted = folder.delete();
            getLog().debug("Deleted " + folder);
            return deleted;
        } else {
            return true;
        }
    }

    private Map<TranslationFile, byte[]> downloadTranslations() throws MojoExecutionException {
        try {
            String uri = "http://api.crowdin.net/api/project/" + authenticationInfo.getUserName()
                    + "/download/all.zip?key=" + authenticationInfo.getPassword();
            getLog().debug("Calling " + uri);
            HttpGet getMethod = new HttpGet(uri);
            HttpResponse response = client.execute(getMethod);
            int returnCode = response.getStatusLine().getStatusCode();
            getLog().debug("Return code : " + returnCode);

            if (returnCode == 200) {

                Map<TranslationFile, byte[]> translations = new HashMap<>();

                InputStream responseBodyAsStream = response.getEntity().getContent();
                ZipInputStream zis = new ZipInputStream(responseBodyAsStream);
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {

                        String name = entry.getName();
                        getLog().debug("Processing " + name);
                        int slash = name.indexOf('/');
                        String language = name.substring(0, slash);
                        name = name.substring(slash + 1);
                        slash = name.indexOf('/');
                        if (slash > 0) {
                            String mavenId = name.substring(0, slash);
                            name = name.substring(slash + 1);
                            TranslationFile translationFile = new TranslationFile(language, mavenId, name);

                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            while (zis.available() > 0) {
                                int read = zis.read();
                                if (read != -1) {
                                    bos.write(read);
                                }
                            }
                            bos.close();
                            translations.put(translationFile, bos.toByteArray());
                        }
                    }
                }

                return translations;
            } else {
                throw new MojoExecutionException("Failed to get translations from crowdin");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to call API", e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        if (messagesInputDirectory.exists()) {
            getLog().info("Downloading translations from crowdin.");
            Map<TranslationFile, byte[]> translations = downloadTranslations();

            Set<Artifact> dependencyArtifacts = getAllDependencies();
            Set<String> mavenIds = new HashSet<>();
            for (Artifact artifact : dependencyArtifacts) {
                String mavenId = getMavenId(artifact);
                mavenIds.add(mavenId);
            }

            Map<TranslationFile, byte[]> usedTranslations = new HashMap<>(translations);

            for (TranslationFile translationFile : translations.keySet()) {
                if (!mavenIds.contains(translationFile.getMavenId())) {
                    getLog().debug(translationFile.getMavenId() + " is not a dependency");
                    usedTranslations.remove(translationFile);
                } else {
                    getLog().debug(translationFile.getMavenId() + " is a dependency");
                }
            }

            translations = usedTranslations;
            if (translations.size() == 0) {
                getLog().info("No translations available for this project!");
            } else {

                getLog().info("Cleaning crowdin folder.");
                cleanFolders(translations.keySet());

                getLog().info("Copying translations to crowdin folder.");
                try {
                    copyTranslations(translations);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to write file", e);
                }

            }
        } else {
            getLog().info(messagesInputDirectory.getPath() + " not found - skipping pull");
        }

    }

    private void copyTranslations(Map<TranslationFile, byte[]> translations) throws IOException {
        Set<Entry<TranslationFile, byte[]>> entrySet = translations.entrySet();
        for (Entry<TranslationFile, byte[]> entry : entrySet) {
            TranslationFile translationFile = entry.getKey();

            byte[] bytes = entry.getValue();
            SortedProperties properties = new SortedProperties();
            InputStream inStream = new ByteArrayInputStream(bytes);
            properties.load(inStream);
            inStream.close();

            File languageFolder = new File(messagesOutputDirectory, translationFile.getLanguage());
            if (!languageFolder.exists()) {
                languageFolder.mkdirs();
            }
            File mavenIdFolder = new File(languageFolder, translationFile.getMavenId());
            if (!mavenIdFolder.exists()) {
                mavenIdFolder.mkdirs();
            }
            File targetFile = new File(mavenIdFolder, translationFile.getName());

            getLog().info(
                    "Importing from crowdin " + translationFile.getLanguage() + "/" + translationFile.getMavenId()
                            + "/" + translationFile.getName());

            FileOutputStream out = new FileOutputStream(targetFile);
            properties.store(out, AggregateCrowdinMojo.COMMENT);
            out.close();

        }
    }

    private Set<Artifact> getAllDependencies() throws MojoExecutionException {
        Set<Artifact> result = new HashSet<>();
        try {
            ArtifactFilter artifactFilter = new ScopeArtifactFilter(DefaultArtifact.SCOPE_COMPILE);

            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

            DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);

            CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();

            rootNode.accept(visitor);

            List<DependencyNode> nodes = visitor.getNodes();
            for (DependencyNode dependencyNode : nodes) {
                result.add(new SpecialArtifact(dependencyNode.getArtifact()));
            }
        } catch (DependencyGraphBuilderException e) {
            throw new MojoExecutionException("Failed to get dependencies", e);
        }
        return result;
    }

}
