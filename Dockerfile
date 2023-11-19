FROM tomcat:9-jdk21-openjdk-slim

# - mount /etc/dsg/, /var/log/tomcat, /var/log/dsg outside of container, maybe /var/lib/dsg

# fish and bob
RUN apt update
RUN apt install -y curl fish git
RUN curl -L https://get.oh-my.fish > install
RUN fish install --noninteractive
RUN fish -c "omf install bobthefish"
RUN mkdir -p ~/.config/fish/functions
RUN echo "function l\n  ls -Alh \$argv\nend" > ~/.config/fish/functions/l.fish
RUN rm install

# set to linux/amd64 for deployment
ARG DOCKER_DEFAULT_PLATFORM
# AstroNvim for linux/amd64
RUN if [ "$DOCKER_DEFAULT_PLATFORM" = "linux/amd64" ]; then  \
    curl -LO https://github.com/neovim/neovim/releases/download/v0.9.0/nvim-linux64.tar.gz; \
    tar xzf nvim-linux64.tar.gz; \
    cp -r nvim-linux64/* /usr; \
    rm -rf nvim-linux64 nvim-linux64.tar.gz; \
  fi
# AstroNvim for Apple Silicon
RUN if [ "$DOCKER_DEFAULT_PLATFORM" = "" ]; then \
    curl -LO https://github.com/matsuu/neovim-aarch64-appimage/releases/download/v0.9.0/nvim-v0.9.0-aarch64.appimage; \
    chmod u+x nvim-v0.9.0-aarch64.appimage; \
    ./nvim-v0.9.0-aarch64.appimage --appimage-extract; \
    cp -r squashfs-root/* /; \
    rm -rf squashfs-root nvim-v0.9.0-aarch64.appimage; \
  fi
RUN git clone --depth 1 https://github.com/AstroNvim/AstroNvim ~/.config/nvim
RUN nvim --headless +PlugInstall +qall

RUN apt install -y ant
RUN mkdir -p /usr/local/tomcat/webapps/ROOT
# copy the pages
COPY dsg_src/httpdocs/ /usr/local/tomcat/webapps/ROOT/
# copy the libs
COPY dsg_src/lib/* /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
RUN mkdir -p /usr/local/tomcat/webapps/tmp_src
# copy the code
COPY dsg_src/java /usr/local/tomcat/webapps/tmp_src
COPY build-docker.xml /usr/local/tomcat/webapps/
# clean the code
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/opengl/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/gameServer/tourney/test/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/message/test/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/turnBased/test/
# compile the code
RUN ant -f /usr/local/tomcat/webapps/build-docker.xml
# cleanup
RUN rm -rf /usr/local/tomcat/webapps/tmp_src
RUN rm /usr/local/tomcat/webapps/build-docker.xml

RUN mkdir -p /var/lib/dsg/gameServer/game
RUN mkdir -p /var/lib/dsg/gameServer/player

# copy the other domains
COPY submanifolddomains/ /usr/local/tomcat/

# local context doesn't access remote instance
ARG ENV=""
RUN mv /usr/local/tomcat/webapps/ROOT/META-INF/${ENV}context.xml /usr/local/tomcat/webapps/ROOT/META-INF/context.xml

# copy the react components (make sure they're built)
COPY ../react-live-game-room/build /usr/local/tomcat/webapps/ROOT/gameServer/live
COPY ../react-mmai/build /usr/local/tomcat/webapps/ROOT/gameServer/mmai
