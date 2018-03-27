import groovy.json.JsonBuilder
import models.*
import templates.*

import hudson.FilePath
import groovy.json.JsonSlurper

void main() {
    def baseFolder = 'devops-jobs'
    def devBaseFolder = "${baseFolder}-dev"

    createFolder(baseFolder, "CI/CD Pipeline")
    createFolder(devBaseFolder, "CI Pipeline for Development")

    def jobs = [["project": "zhengt", "repository": "devops-seeder"], ["project": "zhengt", "repository": "cloud-ms"]]

    jobs.each { job ->
        // create project folder
        createFolder("${baseFolder}/${job.project}", job.project)
        createFolder("${devBaseFolder}/${job.project}", job.project)

        //create jobs
        createJobs(baseFolder, devBaseFolder, job)
    }
}

/**
 * Wrapper function to create a folder in Jenkins
 * @param name
 * @param displayName
 */
void createFolder(String name, String dispName) {
    folder(name) {
        displayName(dispName)
    }
}

/**
 * Generic function for creating jobs given global and project config
 * @param globalConfig the global config used for the job creation
 * @param projectConfig the project config used for the job creation
 */
void createJobs(def baseFolder, def devBaseFolder, def jobConfig) {
    def project = jobConfig.project
    def repositoryNameSlug = jobConfig.repository.trim().replaceAll(" ", "-").toLowerCase()

    // create repo folder
    createFolder("${baseFolder}/${project}/${repositoryNameSlug}", jobConfig.repository)

    createJob("API-Smoke-Test", "master", "apiSmokeTest", jobConfig, baseFolder)
    createJob("API-Integration-Test", "master", "apiIntegrationTest", jobConfig, baseFolder)
    createJob("API-Regression-Test", "master", "apiRegressionTest", jobConfig, baseFolder)

    createJob("Performance-Test", "master", "performanceTest", jobConfig, baseFolder)

    createJob("UI-Smoke-Test", "master", "uiSmokeTest", jobConfig, baseFolder)
    createJob("UI-Integration-Test", "master", "uiIntegrationTest", jobConfig, baseFolder)
    createJob("UI-Regression-Test", "master", "uiRegressionTest", jobConfig, baseFolder)

    createJob("UnitTest", "master", "buildTest", jobConfig, baseFolder)
    createJob("Sonarqube", "master" ,"sonarqube", jobConfig, baseFolder)
    createJob("Veracode", "master" ,"veracode", jobConfig, baseFolder)
    createJob("SonatypeIQ", "master" ,"sonatypeIQ", jobConfig, baseFolder)
    createJob("PackageToNexus", "master", "publish", jobConfig, baseFolder)

    createJob("UnitTestPR", "pr", "buildTest", jobConfig, baseFolder)
    createJob("SonarqubePR", "pr", "sonarqube", jobConfig, baseFolder)
    createJob("SonatypeIQPR", "pr", "sonatypeIQ", jobConfig, baseFolder)
}

/*
* This is a generic function to create Jobs in Jenkins.
*/
void createJob(jobName, jobType, fileName, jobConfig, folder) {
    //make job name slug friendly :)
    def project = jobConfig.project
    def repositoryNameSlug = jobConfig.repository.trim().replaceAll(" ", "-").toLowerCase()
    jobConfig.jobType = fileName
    pipelineJob("${folder}/${project}/${repositoryNameSlug}/${jobName}") {
        logRotator {
            numToKeep(5)
        }
        parameters {
            stringParam("gitUrl", 'https://github.com', 'The Git URL where your project is located. This value is normally https://code.td.com/scm')
            stringParam("project", project, 'The project key associated with the application.')
            stringParam("repository", repositoryNameSlug, 'The repository name of your application within the project.')
            stringParam("branch", jobConfig.branch, 'The default branch to be built in the repository. This can also be a commit hash.')
            stringParam("reportingData", '', 'A string containing reporting data which will be recorded by Bork.')
            if (fileName == 'publish') {
                choiceParam("versionType", ['dev', 'patch', 'minor', 'major'], 'Defines how to semantically version the application in the form of major.minor.patch-dev.')
            } else if (fileName == 'apiTest' || fileName == 'uiTest' || fileName == 'integrationTest' || fileName == 'soaTest' || fileName == 'jmeterTest' || fileName == 'restAssuredTest') {
                stringParam("targetUrl", '', "Used in Integration, UI and API testing as the URL where your application is deployed.")
            }
            if (jobType == 'pr') {
                stringParam("toBranch", '', "Used in Pull Request testing to validate if a merge (without commit) will succeed from your source branch to this branch.")
            }
        }
        definition {
            cps {
                script(template(jobConfig, jobType, fileName))
                sandbox(true)
            }
        }
    }
}

/*
 * This templates pipeline files
 */
String template(jobConfig, jobType, fileName) {
    def templateConfig = jobConfig

    def ciPipelinetemplate = '''\
        @Library("devops-shared-lib@master") _
        
        ciPipelineJob {
          jobType = "$jobType"
        }
    '''.stripIndent()

    return new groovy.text.SimpleTemplateEngine().createTemplate(ciPipelinetemplate).make(templateConfig).toString()
}

main()
