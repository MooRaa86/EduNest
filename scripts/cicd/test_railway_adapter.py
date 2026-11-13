import unittest
import io
from types import SimpleNamespace

from production_gate import GateFailure
from railway_adapter import deployment_status, deploy_immutable_image, list_deployments, read_project_token, verify_project_token


class RailwayAdapterTests(unittest.TestCase):
    def test_documented_project_token_query(self):
        class Response(io.BytesIO):
            status = 200
        def open_fake(request, timeout):
            self.assertEqual(request.get_header("Project-access-token"), "token")
            self.assertIn(b"projectToken", request.data)
            return Response(b'{"data":{"projectToken":{"projectId":"p","environmentId":"e"}}}')
        self.assertTrue(verify_project_token(read_project_token("token", open_fake), "p", "e"))

    def test_project_token_requires_exact_identity_and_no_graphql_errors(self):
        valid = {"data": {"projectToken": {"projectId": "p", "environmentId": "e"}}}
        self.assertTrue(verify_project_token(valid, "p", "e"))
        for response in ({**valid, "errors": [{"message": "Not Authorized"}]}, {"data": {}}, {}):
            with self.assertRaises(GateFailure): verify_project_token(response, "p", "e")
        with self.assertRaises(GateFailure): verify_project_token(valid, "p", "other")

    def test_list_only_accepts_documented_id_and_success_status(self):
        def runner(command, **kwargs):
            self.assertEqual(command[:3], ["railway", "deployment", "list"])
            return SimpleNamespace(returncode=0, stdout='[{"id":"d1","status":"SUCCESS"}]')
        rows = list_deployments("s", "e", {"RAILWAY_TOKEN": "secret"}, runner)
        self.assertEqual(deployment_status(rows, "d1"), "SUCCESS")
        for value in ([], [{"id": "d1", "status": "FAILED"}], [{"id": "d1", "status": "SUCCESS"}] * 2):
            with self.assertRaises(GateFailure): deployment_status(value, "d1")

    def test_unsupported_mutation_fails_without_calling_provider(self):
        with self.assertRaises(GateFailure): deploy_immutable_image()


if __name__ == "__main__": unittest.main()
