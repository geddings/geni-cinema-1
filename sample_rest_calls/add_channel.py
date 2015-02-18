import httplib
import json

class GENICinemaTester(object):

    def __init__(self, server):
        self.server = server

    def get(self, data):
        ret = self.rest_call({}, 'GET')
        return json.loads(ret[2])

    def post(self, data):
        ret = self.rest_call(data, 'POST')
        return ret[0] == 200

    def delete(self, objtype, data):
        ret = self.rest_call(data, 'DELETE')
        return ret[0] == 200

    def rest_call(self, data, action):
        path = '/wm/geni-cinema/add/json'
        headers = {
            'Content-type': 'application/json',
            'Accept': 'application/json',
            }
        body = json.dumps(data)
        conn = httplib.HTTPConnection(self.server, 8080)
        conn.request(action, path, body, headers)
        response = conn.getresponse()
        ret = (response.status, response.reason, response.read())
        print ret
        conn.close()
        return ret

gc = GENICinemaTester('127.0.0.1')

addChannel = {
    "name":"Channel A",
    "description":"Test Channel A",
    "view_password":"12345",
    "admin_password":"abcde"
}

addChannel2 = {
    "name":"Channel B",
    "description":"Test Channel B",
    "view_password":"12345",
    "admin_password":"abcde"
}

addChannel3 = {
    "name":"Channel C",
    "description":"Test Channel C",
    "view_password":"12345",
    "admin_password":"abcde"
}

gc.post(addChannel)
gc.post(addChannel2)
gc.post(addChannel3)
