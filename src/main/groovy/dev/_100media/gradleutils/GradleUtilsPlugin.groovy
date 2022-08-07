package dev._100media.gradleutils

import org.gradle.api.Plugin
import org.gradle.api.Project

class GradleUtilsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        GradleUtilsExtension extension = project.extensions.create("hundredMedia", GradleUtilsExtension.class, project)
    }
}
