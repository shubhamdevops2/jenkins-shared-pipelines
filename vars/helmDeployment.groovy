import java.util.regex.Pattern
import java.util.regex.Matcher

def call(body){

    def prjName = "HELMDEPL"
    def repoName = REPONAME
    def featureimage = FEATUREIMAGE
    def ticket = JIRA_TICKET.trim()
    def envconfigTag = NAMESPACE

    def dockerImagePath = ""
    def dockerimage = ""
    def tagsCaptured = ""
    def findImagePath = ""
    def newyaml = ""
    //def tagPattern = /[0-9]{12}\.[a-z]{3}\.[a-z]{3}\.[a-zA-Z0-9-]{9}\.[a-zA-Z-.\/]{2,}\:[0-9a-zA-Z._-]{2,}/
    def tagPattern = /[a-zA-Z0-9-]{11}[a-zA-Z-.\/]{2,}\:[0-9a-zA-Z._-]{2,}/
    def deployImageList = []
    def dockerImagePathList = []
    def buildVersion = 0
    def pipelineVars
    def deployRepoURL = "git@github.com:shubhamdevops1/${repoName}.git"
    
    node("test"){

        
        tagsCaptured = validateImage(tagPattern, featureimage)

        if(!tagsCaptured.trim()){
            println """
                Invalid image were specified. Please valiate the following images:
                -----------------------------------------------------------------
                ${featureimage}
                -----------------------------------------------------------------
            """

            currentBuild.result = 'ABORTED'
            return
        }

        if(ticket == ""){
            println "No jira ticket specified"
            sh "exit 1"
        }

        deployImageList = tagsCaptured.split(" ").findAll { item -> !(item.isEmpty()) }.unique().sort()
        findImagePath = deployImageList[0]
        println "findImagePath "+findImagePath

        deployImageList.eachWithIndex { tagValue, i ->
            newyaml += "DOCKER_APP_IMAGE_"+(++i)+": "+tagValue+"\n"
        }
        println newyaml

         

        stage("checkout scm"){
            checkout scm: [$class: 'GitSCM',
                            branches: [[name: "main"]],
                            userRemoteConfigs: [[credentialsId: 'github-cred-with-username', url: deployRepoURL]]
            ]    
        }

        stage("Intialise workspace"){
            try{
                for(int i = 0; i < deployImageList.size(); i++){
                    dockerimage = deployImageList[i]
                    echo "dockerimage : " + dockerimage
                    dockerImagePath = sh(script: "grep -ril \$(echo $dockerimage | sed 's/:.*//') charts/", returnStdout: true).trim()
                    echo "dockerImagePath : " + dockerImagePath
                    dockerImagePathList = dockerImagePath.split("\n")

                    if(dockerImagePath != null){
                        for(int j = 0; j < dockerImagePathList.size(); j++ ){
                            dockerImagePath = dockerImagePathList[j]

                            sh "sed -i '/DOCKER_APP_IMAGE:/c DOCKER_APP_IMAGE: $dockerimage' $dockerImagePath"
                            //sh "git add ${dockerImagePath}"

                            sh "cat ${dockerImagePath}"
                            sh '''
                            pwd
                            ls -la
                            '''

                            stage("Update repo"){
                                sshagent(['github-real-cred-with-username']){
                                    sh """ 
                                        git add ${dockerImagePath} 
                                        git commit -m "pushes docker image - ${dockerImagePath}"
                                        git push -f origin HEAD:main
                                        """
                                }    
                            }
                        }
                    }
                    else{
                        echo "Could not find values.yaml for " + dockerimage
                        sh("exit 1")
                    }
                }

                

                pipelineVars = singleDeployment(deployRepoURL, envconfigTag, repoName, "main")
                println "INit: ${pipelineVars}"
            }
            catch(Exception e){
                println "Deployment failed"
                println "${e}"
            }
        }
    }
}

@NonCPS
def validateImage(tagPattern, featureimage){
    def tags=""
    Pattern pattern = Pattern.compile(tagPattern)
    Matcher matcher = pattern.matcher(featureimage)
    
    while(matcher.find()){
        tags = tags+matcher.group()+" ";
    }
    return tags
}