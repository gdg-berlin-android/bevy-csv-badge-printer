source global_vars.sh

curl --verbose \
  -X PUT \
  'https://gdg.community.dev/api/attendee/checkin/' \
  -H "authorization: Token $BEVY_TOKEN" \
  -H 'x-bevy-app: Bevy Oraganizer App' \
  -H 'content-type: application/json' \
  -d '{"event":'$BEVY_EVENT_ID',"attendees":[{"id":'$BEVY_ATTENDEE_ID',"is_checked_in":'$BEVY_ATTENDEE_CHECKED_IN'}]}' |
  jq |
  bat
