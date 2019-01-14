#!groovy
def call(String type,Map map) {
    if (type == 'maven') {
        pipeline {
            agent any
            //参数化变量,目前只支持[booleanParam, choice, credentials, file, text, password, run, string]这几种参数类型
            parameters {
                choice(name:'scene',choices:'scene1:完整流水线\nscene2:单元测试\nscene3:代码检查\nscene4:安全组件检查\nscene5:测试部署',description: '场景选择，默认运行完整流水线，如果只做开发自测可选择代码检查，如果只做环境部署可选择测试部署')
                string(name:'repoBranch', defaultValue: "${map.repoBranch}", description: 'git分支名称')
                choice(name:'server',choices:'10.10.10.23,9001,***,***\n10.10.10.114,9001,***,***',description:'测试环境地址（IP+Tomcat端口+name+password）')
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

            triggers {
                pollSCM('H 13 * * 1-5')
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

                            //单元测试运行场景
                            isUT=params.scene.contains('scene1:完整流水线') || params.scene.contains('scene2:单元测试')
                            println "isUT="+isUT

                            //静态代码检查运行场景
                            isCA=params.scene.contains('scene1:完整流水线') || params.scene.contains('scene3:代码检查')
                            println "isCA="+isCA

                            //安全组件检查运行场景
                            isFindBug = params.scene.contains('scene1:完整流水线') || params.scene.contains('scene4:安全组件检查')
                            println "isFindBug="+isFindBug

                            //部署测试环境运行场景
                            isDP=params.scene.contains('scene1:完整流水线') || params.scene.contains('scene5:测试部署')
                            println "isDP="+isDP

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
                            git url:REPO_URL, branch:params.repoBranch
                        }
                    }
                }
                stage ('单元测试') {
                    //when指令允许Pipeline根据给定的条件确定是否执行该阶段,isUT为真时，执行单元测试
                    when { expression {return isUT } }
                    steps {
                        echo "开始使用jacoco进行单元测试**********"
                        sh "mvn org.jacoco:jacoco-maven-plugin:prepare-agent  clean  package  -Dautoconfig.skip=true   -Dmaven.test.skip=false  -Dmaven.test.failure.ignore=true"
                        junit '**/target/surefire-reports/*.xml'
                        //当代码覆盖率低于70%时，构建失败
                        jacoco changeBuildStatus: true, maximumLineCoverage:"70"
                        //注：多项目的工程，需要设置jacoco的destFile属性，合并所有的jacoco.exec报告到多项目工程的ProjectDirectory(根)目录

                    }
                }

                stage ('静态代码扫描') {
                    //when指令允许Pipeline根据给定的条件确定是否执行该阶段,isCA为真时，执行静态代码扫描
                    when { expression {return isCA } }
                    steps{
                        echo "**********开始静态代码扫描！**********"
                        withSonarQubeEnv('SonarQube') {
                            sh "mvn -f pom.xml clean compile sonar:sonar"
                        }
                        script {
                            timeout(120){
                                def qg = waitForQualityGate()
                                if (qg.status != 'OK') {
                                    error "本次扫描未通过Sonarqube的代码质量阈检查，请及时修改！failure: ${qg.status}"
                                }
                            }
                        }
                    }
                }

                stage ('安全组件检查') {
                    //when指令允许Pipeline根据给定的条件确定是否执行该阶段,isFindBug为真时，执行安全组件检查
                    when { expression {return isFindBug } }
                    steps {
                        //指定检查**/lib/*.jar的组件
                        dependencyCheckAnalyzer datadir: '', hintsFile: '', includeCsvReports: false, includeVulnReports: true,includeHtmlReports: true, includeJsonReports: false, isAutoupdateDisabled: false, outdir: '', scanpath: '**/lib/*.jar', skipOnScmChange: false, skipOnUpstreamChange: false, suppressionFile: '', zipExtensions: ''
                        //有高级别组件漏洞时，fail掉pipeline
                        dependencyCheckPublisher canComputeNew: false, defaultEncoding: '', failedTotalHigh: '0', healthy: '', pattern: '', unHealthy: ''
                        archiveArtifacts allowEmptyArchive: true, artifacts: '**/dependency-check-report.xml', onlyIfSuccessful: true
                    }
                }

                stage ('测试环境部署') {
                    when { expression {return isDP } }
                    steps{
                        echo '测试环境部署完成'
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