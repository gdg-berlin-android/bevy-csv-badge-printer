source global_vars.sh

curl --verbose \
  'https://gdg.community.dev/api/user/2914846/' \
  -H "authorization: Token $BEVY_TOKEN" \
  -H 'content-type: application/json' |
  jq |
  less
