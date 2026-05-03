source global_vars.sh

curl --verbose \
  'https://gdg.community.dev/api/attendee/?event='$BEVY_EVENT_ID \
  -H "authorization: Token $BEVY_TOKEN" \
  -H 'content-type: application/json' |
  jq
