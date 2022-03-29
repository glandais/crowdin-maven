package com.googlecode.crowdin.maven;

import org.apache.http.client.methods.HttpGet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Export crowdin translations in this project, for a fresh translation file
 */
@Mojo(name = "export", aggregator = true, threadSafe = true)
public class ExportCrowdinMojo extends AbstractCrowdinMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		super.execute();

		getLog().info("Asking crowdin to export translations");

		String uri = "http://api.crowdin.net/api/project/" + authenticationInfo.getUserName() + "/export?key="
				+ authenticationInfo.getPassword();

		getLog().info(
				"Calling " + "http://api.crowdin.net/api/project/" + authenticationInfo.getUserName() + "/export?key="
						+ "?????");
		HttpGet getMethod = new HttpGet(uri);
		int returnCode;
		try {
			returnCode = client.execute(getMethod).getStatusLine().getStatusCode();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to call API", e);
		}
		getLog().debug("Return code : " + returnCode);
		if (returnCode != 200) {
			throw new MojoExecutionException("Failed to export translations from crowdin");
		}

		// Post doesn't work ?
		// crowdinRequestAPI("export", null, null, true);

	}
}
