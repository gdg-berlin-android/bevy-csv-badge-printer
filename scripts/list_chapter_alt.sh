source global_vars.sh

curl --verbose \
  'https://gdg.community.dev/api/chapter/'$BEVY_CHAPTER_ID'/' \
  -H "authorization: Token $BEVY_TOKEN" \
  -H 'content-type: application/json' |
  jq |
  bat
