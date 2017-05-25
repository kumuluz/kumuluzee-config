#!/usr/bin/env bash

openssl aes-256-cbc -K $encrypted_bc82b4744960_key -iv $encrypted_bc82b4744960_iv -in gpg.tar.gz.enc -out gpg.tar.gz -d
tar xvf gpg.tar.gz

mvn source:jar javadoc:jar deploy -Pdeploy -DskipTests=true --settings ./settings.xml