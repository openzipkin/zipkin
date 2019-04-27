/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

pipeline {
    agent {
        label 'ubuntu'
    }

    tools {
        jdk 'JDK 11 (latest)'
    }

    options {
        buildDiscarder(logRotator(
            numToKeepStr: '30',
        ))
        timestamps()
        skipStagesAfterUnstable()
        timeout time: 30, unit: 'MINUTES'
    }

    stages {
        stage('SCM Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Check environment') {
            steps {
                sh 'env'
                sh 'pwd'
                sh 'ls'
                sh 'git status'
            }
        }

        stage('Run tests') {
            steps {
                // use install, as opposed to verify, to ensure invoker tests use latest code
                // skip docker tests so that we don't take 2hrs to build. Travis will run these.
                sh './mvnw clean install --batch-mode -nsu -Ddocker.skip=true'
            }
        }

        stage('Publish snapshot') {
            when {
                branch 'master'
            }
            steps {
                sh './mvnw deploy -Papache-release -Dgpg.skip=true -DskipTests --batch-mode -nsu'
            }
        }
    }

    post {
        always {
            junit '**/target/*-reports/*.xml'
            deleteDir()
        }

        changed {
            script {
                if (env.BRANCH_NAME == 'master') {
                    emailext(
                        subject: "[${currentBuild.projectName}] master is ${currentBuild.currentResult} (#${currentBuild.number})",
                        to: 'commits@zipkin.apache.org',
                        replyTo: 'dev@zipkin.apache.org',
                        body: "See <${currentBuild.absoluteUrl}>"
                    )
                }
            }

        }
    }
}
