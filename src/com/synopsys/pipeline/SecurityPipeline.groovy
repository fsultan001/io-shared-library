#! /bin/groovy

package com.synopsys.pipeline

import java.nio.channels.Pipe

/**
 * This script is the entry point for the pipeline shared library.
 * It defines pipeline stages and manages overall control flow in the pipeline.
 */

def execute() {
    node('master') {
        stages {
			stage('Checkout Code'){
				steps {
					git branch: 'production', url: 'https://github.com/devsecops-test/github-io-sample'
				}
			}
			
			stage('Building Source...'){
				steps {
				   //your build command, your way here...    
				   //sh '''mvn clean compile'''
				   echo 'build source code'
				}
			}
		 
			stage('IO - Setup Prescription') {
				steps {
					 echo 'Setup Prescription'
					 synopsysIO(connectors: [io(configName: 'iostaging', projectName: 'github-io-sample', workflowVersion: '2021.12.0.10-alpha'), 
					 github(branch: 'production', configName: 'github-io-sample', owner: 'devsecops-test', repositoryName: 'github-io-sample'), 
					 buildBreaker(configName: 'BB-ALL')]) {
						sh 'io --stage io'
					 }
				}
			}
        
			stage('SAST- RapidScan') {
				environment {
					OSTYPE='linux-gnu'   
				}
				steps {
				   echo 'Running SAST using Sigma - Rapid Scan'
				   echo env.OSTYPE
				   synopsysIO(connectors: [rapidScan(configName: 'Sigma')]) {
					   sh 'io --stage execution --state io_state.json'
				   }
				} 
		    }   
		  
			stage('SAST - Polaris') {
				steps {
				   echo 'Running SAST using Polaris'
				   synopsysIO(connectors: [[$class: 'PolarisPipelineConfig', configName: 'csprod-polaris', projectName: 'sig-devsecops/github-io-sample']]) {
					  sh 'io --stage execution --state io_state.json'
				   }
				}
			}
        
			stage('SCA - Black Duck') {
				steps {
				   echo 'Running SCA using Black Duck'
				   synopsysIO(connectors: [blackduck(configName: 'BIZDevBD', projectName: 'devsecops-test/github-io-sample', projectVersion: '1.0')]) {
					  sh 'io --stage execution --state io_state.json'
				   }
				}
			}
        
			stage('IO - Workflow') {
				steps {
					echo 'Execute Workflow Stage'
					synopsysIO() {
						synopsysIO(connectors: [slack(configName: 'io-qa')]) {
					
						 sh 'io --stage workflow --state io_state.json'
						}
					}
				}
			}
		}
    
		post { 
			always { 
				archiveArtifacts artifacts: '**/*-results*.json', allowEmptyArchive: 'true'
				//remove the state json file it has sensitive information
				sh 'rm io_state.json'
			}
		}
    }
}
