FROM mariadb:latest


RUN mv /usr/local/bin/docker-entrypoint.sh /old-docker-entrypoint.sh
COPY dockerMain/custom-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

RUN ln -s /usr/local/bin/docker-entrypoint.sh / # backwards compat

# because debian uid is 1000 and mysql is 999
USER root
RUN usermod -u 1000 mysql
RUN groupmod -g 1000 mysql
RUN sudo find / -group 999 -readable -depth -exec chgrp -h mysql {} +; 2>/dev/null
RUN sudo find / -user 999 -readable -depth -exec chown -h mysql {} +; 2>/dev/null

USER mysql

ENTRYPOINT ["docker-entrypoint.sh"]
EXPOSE 3306
CMD ["mariadbd"]
