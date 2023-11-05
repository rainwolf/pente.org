FROM mariadb:latest


RUN mv /usr/local/bin/docker-entrypoint.sh /old-docker-entrypoint.sh
COPY dockerMain/custom-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

RUN ln -s usr/local/bin/docker-entrypoint.sh / # backwards compat

USER mysql

ENTRYPOINT ["docker-entrypoint.sh"]
EXPOSE 3306
CMD ["mariadbd"]
