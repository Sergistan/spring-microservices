pipeline {
    agent any

    environment {
        // Docker Hub credentials stored in Jenkins (username/password)
        DOCKERHUB_CREDENTIALS = credentials('2458a8bf-c1db-46c5-a39f-a6f5061da077')
        DOCKERHUB_NAMESPACE = 'sergistan'
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }


        stage('Build & Test') {
            steps {
                // Windows‑команда для Gradle
                bat 'gradlew.bat clean test --no-daemon'
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def services = [
                        'eureka-server',
                        'config-server',
                        'getaway-server',
                        'order-service',
                        'shop-service',
                        'payment-service',
                        'notification-service',
                        'history-service'
                    ]
                    for (svc in services) {
                        bat """
                            echo Building image for ${svc}
                            docker build -t ${DOCKERHUB_NAMESPACE}/${svc}:%BUILD_NUMBER% .\\${svc}
                        """
                    }
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                script {
                    // Авторизация в DockerHub
                    docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-creds') {
                        def services = [
                            'eureka-server',
                            'config-server',
                            'getaway-server',
                            'order-service',
                            'shop-service',
                            'payment-service',
                            'notification-service',
                            'history-service'
                        ]
                        for (svc in services) {
                            bat "docker push ${DOCKERHUB_NAMESPACE}/${svc}:%BUILD_NUMBER%"
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Сборка и публикация образов успешно завершены'
        }
        failure {
            echo '❌ Что‑то пошло не так, проверьте логи'
        }
    }
}
