import unittest
from production_identity import check

PRODUCTION_REF = "b" * 20


class ProductionIdentityTests(unittest.TestCase):
    def setUp(self):
        self.staging = "a" * 20
        self.direct = f"jdbc:postgresql://db.{PRODUCTION_REF}.supabase.co:5432/postgres?sslmode=verify-full"

    def verify(self, url=None, user="postgres", approved=PRODUCTION_REF, staging=None,
               database="postgres", schema="public"):
        return check(self.direct if url is None else url, user, approved,
                     self.staging if staging is None else staging, database, schema)

    def test_direct_and_pooler(self):
        self.assertTrue(self.verify())
        self.assertTrue(self.verify("jdbc:postgresql://aws-0-region.pooler.supabase.com:5432/postgres?sslmode=verify-full",
                                    user=f"postgres.{PRODUCTION_REF}"))

    def test_wrong_target_and_missing_configuration(self):
        self.assertFalse(self.verify(approved=self.staging))
        self.assertFalse(self.verify(staging=""))
        self.assertFalse(self.verify(staging=PRODUCTION_REF))
        self.assertFalse(self.verify(database="other"))
        self.assertFalse(self.verify(schema="other"))
        self.assertFalse(self.verify(user="postgres.other"))
        self.assertFalse(self.verify(self.direct.replace(PRODUCTION_REF, self.staging)))

    def test_reject_ambiguous_connections(self):
        for url in (self.direct + "&user=attacker", self.direct + "&host=evil",
                    self.direct.replace("verify-full", "require"),
                    self.direct.replace(":5432", ":6543"),
                    self.direct.replace("//db.", "//user:password@db."),
                    self.direct.replace("/postgres?", "/postgres?sslmode=require&"),
                    self.direct + "#fragment"):
            with self.subTest(url=url):
                self.assertFalse(self.verify(url))


if __name__ == "__main__":
    unittest.main()
