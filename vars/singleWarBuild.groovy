def call(body){

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST   //Its telling the closure (body) to look at the delegate first to find properties 
    body.delegate = config                          //assign the config map as delegate for the body object
    body()                                          //variables are scoped to the config map

    def branch = "main"
    def doBuild = true
    def registryName = 'shubham1769/'
    def originalversion, releaseVersion, newPomVersion, sonarProps, sonarResult
    def npmHome = "/usr/share/npm"
    //def mavenSettings = "/opt/maven/conf/settings.xml"

 
    node("test"){
        properties([
            buildDiscarder(
                logRotator(
                    //artifactDaysToKeepStr: "",
                    artifactNumToKeepStr: "5",
                    //daysToKeepStr: "",
                    numToKeepStr: "5"
                )
            )
        ])

        stage("Checkout scm"){
            //checkout scm
            checkout([$class: 'GitSCM',
                branches: scm.branches,
                extensions: [[$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true, timeout: 15]],
                userRemoteConfigs: scm.userRemoteConfigs
            ])
            //sh "git checkout "		     
        }

        //This stage is to stop rebuild of compenents if nothing has changes in the repo
        stage("Check last commit (if the build stops here there are no changes)"){
            def USER = sh(script: 'git log -1 --format=%an', returnStdout: true).trim()
            sh 'git log -1 --format=%an'
            
            if(USER == "jenkins.docker"){
                echo """####################################################### 
                            | No code change, the last change was to the version
                            | The build has been skipped to avoid the infinite loop
                            | The last user to commit was ${USER}
                            | #######################################################""".stripMargin()

                try{
                    timeout(time: 60, unit: 'SECONDS'){
                        doBuild = input(
                            id: 'Proceed1', 
                            message: 'Tick the box to container build', 
                            parameters: [
                                [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: 'continue to build']
                            ]
                        )
                    }
                }
                catch(err){
                    doBuild = false
                    echo "timeout occured, build is not continuing"
                }
            }

            if(doBuild){
                def ecrRepoName = config.ecrRepoName.trim()
                println "ECR Repo name: $ecrRepoName"
                println "Registry name $registryName"

                //call versioning and work on next maven version
                stage("Get Version Details"){
                    def versionArray
                    versionArray = versioning(true, ecrRepoName, config.targetFile, branch)
                    
                    originalversion = versionArray[0]
                    releaseVersion = versionArray[1]
                    newPomVersion = versionArray[2]
                    imageTag = versionArray[3]

                }

                stage("SONAR : This will trigger next 4 stages"){
                    sonarProps = sonarRunner(npmHome, config.targetFile,releaseVersion)
                    sonarResult = sonarProps['sonarResult']
                }

                stage("Node Results aggregation"){
                    echo "Sonar Result: ${sonarResult}"

                    if( sonarResult == "failure" ){
                        throw new RuntimeException("Sonarqube check has failed, this component is under threshold")
                    }
                    if( sonarResult == "aborted" ){
                        throw new RuntimeException("Sonarqube check has failed, something went wrong during the report")
                    }
                }

                stage("Versioning - creating the package"){
                    sh "npm pack"
                }

                stage("Build & push the artifacts to Jfrog"){
                    withCredentials([string(credentialsId: 'jfrog', variable: 'jfrogCred')]) {
                        def uploadSpec = """{
                        "files": [
                            {
                            "pattern": "nodejs-usermanagement-${releaseVersion}.tgz",
                            "target": "myapp-npm-local/nodejs-usermanagement-${releaseVersion}.tgz"
                            }
                        ]
                        }"""
                        def server = Artifactory.server 'jfrog'
                        def buildInfo = Artifactory.newBuildInfo()
                        buildInfo.env.collect()
                        server.upload spec: uploadSpec, buildInfo: buildInfo
                        server.publishBuildInfo buildInfo
                    }
                }

                stage("Versioning - updating to new release"){
                    sh """
                    sed -i 's/"version": "${releaseVersion}"/"version": "${newPomVersion}"/g' package.json
                    cat package.json
                    """
                }

                stage("Build Docker Image"){
                    sh "id"
                    sh "docker build -t ${imageTag} --file=${config.dockerFile} ."
                }

                stage("Publish docker image"){
                    withCredentials([usernamePassword(credentialsId: 'docker-cred-shubhamdevops2', passwordVariable: 'password', usernameVariable: 'username')]) {
                        sh "docker login -u ${username} -p ${password}"
                        sh "docker push ${imageTag}"
                        sh "docker rmi ${imageTag}"
                        sh "rm nodejs-usermanagement-${releaseVersion}.tgz"
                    }

                }

                stage("Update repo"){
                    sshagent(['shubhamdevops2']){
                        sh "git config --global user.email \"jenkins.docker@gmail.com\" && git config --global user.name \"jenkins.docker\" && \
                            git commit -am '[JENKINS] Built version ${releaseVersion}' && git push -f origin HEAD:main"
                    }    
                }
            }
        }
    }
}