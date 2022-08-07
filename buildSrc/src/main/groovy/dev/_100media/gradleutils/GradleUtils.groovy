package dev._100media.gradleutils

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.authentication.http.BasicAuthentication

class GradleUtils {
    /**
     * Get a closure to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a publishing block.
     * <p>
     * The following environment variables must be set for this to work:
     * <ul>
     *  <li>MAVEN_USER: Contains the username to use for authentication</li>
     *  <li>MAVEN_PASSWORD: Contains the password to use for authentication</li>
     * </ul>
     * The following environment variables are optional:
     * <ul>
     *  <li>MAVEN_URL_RELEASES: Contains the URL to use for the release repository</li>
     *  <li>MAVEN_URL_SNAPSHOTS: Contains the URL to use for the snapshot repository</li>
     * </ul>
     *
     * @param project the project
     * @param defaultReleasesFolder the default releases folder if the required maven information is not currently set
     * @param defaultSnapshotsFolder the default snapshots folder if the required maven information is not currently set
     * @return a closure
     */
    static getPublishing100MediaMaven(Project project, File defaultReleasesFolder = project.rootProject.file('repo'), File defaultSnapshotsFolder = project.rootProject.file('repo')) {
        return setupSnapshotCompatiblePublishing(project, 'https://maven.100media.dev/main-releases', 'https://maven.100media.dev/main-snapshots',
                defaultReleasesFolder, defaultSnapshotsFolder)
    }

    static getMavenUser(Project project) {
        return project.hasProperty('hm_maven_username') ? project.property('hm_maven_username') : System.env.MAVEN_USER
    }

    static getMavenPassword(Project project) {
        return project.hasProperty('hm_maven_password') ? project.property('hm_maven_password') : System.env.MAVEN_PASSWORD
    }

    /**
     * Get a closure to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a publishing block, this closure respects the current project's version,
     * with regards to publishing to a release or snapshot repository.
     * <p>
     * The following environment variables must be set for this to work:
     * <ul>
     *  <li>MAVEN_USER: Contains the username to use for authentication</li>
     *  <li>MAVEN_PASSWORD: Contains the password to use for authentication</li>
     * </ul>
     * The following environment variables are optional:
     * <ul>
     *  <li>MAVEN_URL_RELEASES: Contains the URL to use for the release repository</li>
     *  <li>MAVEN_URL_SNAPSHOTS: Contains the URL to use for the snapshot repository</li>
     * </ul>
     *
     * If MAVEN_URL_RELEASES is not set, the provided fallback URL will be used for the release repository.
     *
     * @param project the project
     * @param fallbackReleasesEndpoint the fallback URL to use for the releases repository if MAVEN_URL_RELEASES is not set
     * @param fallbackSnapshotsEndpoint the fallback URL to use for the snapshots repository if MAVEN_URL_SNAPSHOTS is not set
     * @param defaultReleasesFolder the default releases folder if the required maven information is not currently set
     * @param defaultSnapshotsFolder the default snapshots folder if the required maven information is not currently set
     * @return a closure
     */
    static setupSnapshotCompatiblePublishing(Project project, String fallbackReleasesEndpoint, String fallbackSnapshotsEndpoint,
                                             File defaultReleasesFolder, File defaultSnapshotsFolder) {
        return { MavenArtifactRepository it ->
            name '100Media'
            boolean isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
            def mavenUser = getMavenUser(project)
            def mavenPassword = getMavenPassword(project)
            if (mavenUser && mavenPassword) {
                if (isSnapshot && System.env.MAVEN_URL_SNAPSHOTS) {
                    url System.env.MAVEN_URL_SNAPSHOTS
                } else if (System.env.MAVEN_URL_RELEASES) {
                    url System.env.MAVEN_URL_RELEASES
                } else {
                    url isSnapshot ? fallbackSnapshotsEndpoint : fallbackReleasesEndpoint
                }

                authentication {
                    basic(BasicAuthentication)
                }
                credentials {
                    username = mavenUser
                    password = mavenPassword
                }
            } else {
                url 'file://' + (isSnapshot ? defaultSnapshotsFolder : defaultReleasesFolder).getAbsolutePath()
            }
        }
    }

    static setup100MediaCredentials(Project project, MavenArtifactRepository repo) {
        if (System.env.MAVEN_USER && System.env.MAVEN_PASSWORD) {
            repo.credentials {
                username = getMavenUser(project)
                password = getMavenPassword(project)
            }
            repo.authentication {
                basic(BasicAuthentication)
            }
        }
    }

    /**
     * Get a closure for the 100 Media maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @param the project
     * @return a closure
     */
    static get100MediaMaven(Project project) {
        return { MavenArtifactRepository it ->
            name '100Media'
            url 'https://maven.100media.dev/'
            setup100MediaCredentials(project, it)
        }
    }

    /**
     * Get a closure for the 100 Media releases maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @param the project
     * @return a closure
     */
    static get100MediaReleasesMaven(Project project) {
        return { MavenArtifactRepository it ->
            name '100Media-releases'
            url 'https://maven.100media.dev/releases'
            setup100MediaCredentials(project, it)
        }
    }

    /**
     * Get a closure for the 100 Media snapshots maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @param project the project
     * @return a closure
     */
    static get100MediaSnapshotsMaven(Project project) {
        return { MavenArtifactRepository it ->
            name '100Media-snapshots'
            url 'https://maven.100media.dev/snapshots'
            setup100MediaCredentials(project, it)
        }
    }
}
