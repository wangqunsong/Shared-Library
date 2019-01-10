def call(String type,Map map) {
    if (type == 'maven') {
        pipeline {
            agent any
            //参数化变量,目前只支持[booleanParam, choice, credentials, file, text, password, run, string]这几种参数类型
            parameters {
                choice(name:'scene',choices:"scene1:完整流水线\nscene2:代码检查\nscene3:测试部署",description: '场景选择，默认运行完整流水线，如果只做开发自测可选择代码检查，如果只做环境部署可选择测试部署')
                choice(name: 'server',choices:'192.168.1.107,9090,***,***\n192.168.1.60,9090,***,***', description: '测试服务器列表选择(IP,TomcatPort,Name,Passwd)')
                string(name:'dubboPort', defaultValue: '31100', description: '测试服务器的dubbo服务端口')
                //单元测试代码覆盖率要求，各项目视要求调整参数
                string(name:'lineCoverage', defaultValue: '20', description: '单元测试代码覆盖率要求(%)，小于此值pipeline将会失败！')
                //若勾选在pipelie完成后会邮件通知测试人员进行验收
                booleanParam(name: 'isCommitQA',description: '是否在pipeline完成后，邮件通知测试人员进行人工验收',defaultValue: true )
            }
            //环境变量，初始确定后一般不需更改
            tools {
                maven "${map.maven}"
                jdk   "${map.jdk}"
            }
            //常量参数，初始确定后一般不需更改
            environment{
                REPO_URL="${map.REPO_URL}"
                //git服务全系统只读账号，无需修改
                CRED_ID="${map.CRED_ID}"
                //pom.xml的相对路径
                POM_PATH="${map.POM_PATH}"
                //生成war包的相对路径
                WAR_PATH="${map.WAR_PATH}"
                //测试人员邮箱地址
                QA_EMAIL="${map.QA_EMAIL}"
                //接口测试job名称
                ITEST_JOBNAME="${map.ITEST_JOBNAME}"
            }

            options {
                disableConcurrentBuilds()
                timeout(time: 1, unit: 'HOURS')
                //保持构建的最大个数
                buildDiscarder(logRotator(numToKeepStr: '10'))
            }
            post {
                success {
                    script {
                        wrap([$class: 'BuildUser']) {
                            mail to: "${BUILD_USER_EMAIL }",
                                    subject: "PineLine项目 '${JOB_NAME}' (${BUILD_NUMBER}) 构建结果",
                                    body: "${BUILD_USER}的 pineline项目 '${JOB_NAME}' (${BUILD_NUMBER}) 构建【成功】！\n请及时前往${env.BUILD_URL}进行查看！"
                        }
                    }
                }
                failure{
                    script {
                        wrap([$class: 'BuildUser']) {
                            mail to: "${BUILD_USER_EMAIL }",
                                    subject: "PineLine项目 '${JOB_NAME}' (${BUILD_NUMBER}) 构建结果",
                                    body: "${BUILD_USER}的 pineline项目 '${JOB_NAME}' (${BUILD_NUMBER}) 构建【失败】！\n请及时前往${env.BUILD_URL}进行查看！"
                        }
                    }

                }
                unstable{
                    script {
                        wrap([$class: 'BuildUser']) {
                            mail to: "${BUILD_USER_EMAIL }",
                                    subject: "PineLine项目 '${JOB_NAME}' (${BUILD_NUMBER}) 构建结果",
                                    body: "${BUILD_USER}的 pineline项目 '${JOB_NAME}' (${BUILD_NUMBER}) 构建【未知】！\n请及时前往${env.BUILD_URL}进行查看！"
                        }
                    }
                }
            }
            stages {
                stage ('代码获取'){
                    steps {
                        script {
                            def split=params.server.split(",")
                            serverIP=split[0]
                            jettyPort=split[1]
                            serverName=split[2]
                            serverPasswd=split[3]

                            //选择场景
                            println params.scene
                            isUT=params.scene.contains('scene1:完整流水线') || params.scene.contains('scene2:代码检查')
                            println "isUT="+isUT
                            //静态代码检查运行场景
                            isCA=params.scene.contains('scene1:完整流水线') || params.scene.contains('scene2:代码检查')
                            println "isCA="+isCA
                            //部署测试环境运行场景
                            isDP=params.scene.contains('scene1:完整流水线') || params.scene.contains('scene3:测试部署')
                            println "isDP="+isDP
                            isDC=params.scene.contains('scene1:完整流水线')
                            println "isDC="+isDC
                            //接口测试运行场景
                            isIT=params.scene.contains('scene1:完整流水线')
                            println "isIT="+isIT
                            try{
                                wrap([$class: 'BuildUser']){
                                    userEmail="${BUILD_USER_EMAIL},${QA_EMAIL}"
                                    user="${BUILD_USER_ID}"
                                }
                            }catch(exc){
                                userEmail="${QA_EMAIL}"
                                user="system"
                            }
                            echo "**********开始从${params.repoURL}获取代码**********"
                            git url:params.repoURL, branch:params.repoBranch
                        }
                    }
                }
                stage ('单元测试') {
                    steps {
                        script {
                            return isUT
                        }
                    }
                }
            }

        }
    }
    else if (type == 'gradle'){
        pipeline {
            agent any
            echo "gradle技术栈未配置"
        }
    }
}