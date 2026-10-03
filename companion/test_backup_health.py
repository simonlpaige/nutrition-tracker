import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from backup_health import sha, verify,phone
from unittest.mock import patch


class BackupVerificationTests(unittest.TestCase):
    def archive(self, path, journal, digest=None):
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('phone/files/journal.json', journal)
            z.writestr('manifest.json', json.dumps({'sha256': {
                'phone/files/journal.json': digest or sha(journal)}}))

    def test_valid_backup_can_be_read_and_verified(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'backup.zip'
            self.archive(p, b'{"version":1,"meals":[]}')
            self.assertIn('sha256', verify(p))

    def test_changed_content_fails_checksum(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'backup.zip'
            self.archive(p, b'{"version":1,"meals":[{}]}', sha(b'{"version":1,"meals":[]}'))
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                verify(p)

    def test_valid_checksum_does_not_hide_invalid_journal(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'backup.zip'
            self.archive(p, b'{"version":0,"meals":[]}')
            with self.assertRaisesRegex(ValueError, 'Invalid journal'):
                verify(p)

    def test_pending_room_export_is_not_marked_verified(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'backup.zip';journal=b'{"version":1,"meals":[]}';self.archive(p,journal)
            with zipfile.ZipFile(p,'a') as z:z.writestr('phone/files/journal-export-status.json',json.dumps({'valid':False}))
            with self.assertRaisesRegex(ValueError,'pending or stale'):verify(p)

    def test_credential_storage_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'backup.zip';self.archive(p,b'{"version":1,"meals":[]}')
            with zipfile.ZipFile(p,'a') as z:z.writestr('phone/shared_prefs/ai_secure.xml','encrypted credential fixture')
            with self.assertRaisesRegex(ValueError,'credential'):verify(p)

    def test_phone_selection_ignores_test_emulator(self):
        with patch('backup_health.subprocess.check_output',return_value='List of devices attached\nreal-phone\tdevice\nemulator-5580\tdevice\n'):
            self.assertEqual(phone('adb'),['adb','-s','real-phone'])


if __name__ == '__main__':
    unittest.main()
