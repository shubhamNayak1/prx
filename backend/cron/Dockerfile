# Tiny image used by every Render cron job in this blueprint.
# Each cron sets TARGET_URL + CRON_KEY as env vars and the container just
# POSTs to that endpoint with the shared-secret header, then exits.
FROM curlimages/curl:latest
CMD ["sh", "-c", "curl -fsS -X POST -H \"x-cron-key: ${CRON_KEY}\" \"${TARGET_URL}\""]
