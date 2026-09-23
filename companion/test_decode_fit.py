import datetime as dt
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from decode_fit import decode

class ImportTests(unittest.TestCase):
 def test_filters_codes_deduplicates_and_removes_identifiers(self):
  now=dt.datetime(2026,9,20,23,tzinfo=dt.timezone.utc)
  at=now-dt.timedelta(hours=1)
  class DecoderFixture:
   def __init__(self,stream):self.consumed=False
   def is_fit(self):return True
   def check_integrity(self):self.consumed=True;return True
   def read(self):
    if self.consumed:return {},[]
    return {'file_id_mesgs':[{'serial_number':12345}],
      'stress_level_mesgs':[{'stress_level_time':at,'stress_level_value':-2},{'stress_level_time':at,'stress_level_value':42}],
      'respiration_rate_mesgs':[{'timestamp':at,'respiration_rate':-1}],
      'session_mesgs':[{'start_time':at,'timestamp':now,'sport':'walking','start_position_lat':123,'total_timer_time':300}],408:[{0:123}]},[]
  with tempfile.TemporaryDirectory() as folder:
   for name in ['a.FIT','b.FIT']:Path(folder,name).write_bytes(b'fixture')
   with patch('decode_fit.Decoder',DecoderFixture),patch('decode_fit.Stream.from_file',lambda p:p):
    result=decode(folder,now)
  self.assertEqual(len(result['samples']),1)
  self.assertEqual(result['samples'][0]['value'],42)
  self.assertEqual(len(result['activities']),1)
  self.assertNotIn('position',str(result))
  self.assertNotIn('serial_number',str(result))
  self.assertIn('408',result['unknownMessageTypes'])
 def test_invalid_integrity_is_reported(self):
  with tempfile.TemporaryDirectory() as folder:
   Path(folder,'bad.FIT').write_bytes(b'not a fit file')
   result=decode(folder)
  self.assertEqual(result['filesRead'],0)
  self.assertEqual(result['samples'],[])
  self.assertTrue(result['issues'])

if __name__=='__main__':unittest.main()
