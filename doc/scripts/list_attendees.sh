source global_vars.sh

curl --verbose \
  'https://gdg.community.dev/api/event/'$BEVY_EVENT_ID'/attendee/' \
  -H "authorization: Token $BEVY_TOKEN" \
  -H 'content-type: application/json' |
  jq |
  less
