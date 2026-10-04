"""Canonical property migration checks; application execution is recorded separately."""
from pathlib import Path
import importlib.util, unittest

spec=importlib.util.spec_from_file_location('historical_upgrade',Path(__file__).with_name('HistoricalUpgrade.py'))
upgrade=importlib.util.module_from_spec(spec); spec.loader.exec_module(upgrade)

class QueryPropertyTests(unittest.TestCase):
    def test_changes_only_active_complete_line_and_keeps_comment_and_custom_value(self):
        prefix=b'# Preserve customer note: spring.jdbc.template.query-timeout=1500ms\ncustomer.example=spring.jdbc.template.query-timeout=1500ms\n'
        suffix=b'# Already noted: spring.jdbc.template.query-timeout=1s\ncustomer.name=orders-north\n'
        before=prefix+b'spring.jdbc.template.query-timeout=1500ms\n'+suffix
        expected=prefix+b'spring.jdbc.template.query-timeout=1s\n'+suffix
        after=upgrade.rewrite_query_property(before)
        self.assertEqual(after,expected)
        # Oracle uses independently specified byte spans, never the implementation's rewrite/remove helper.
        self.assertEqual(after[:len(prefix)],prefix)
        self.assertEqual(after[-len(suffix):],suffix)

    def test_refuses_missing_duplicate_already_changed_or_noncanonical_active_line(self):
        for before in (b'# spring.jdbc.template.query-timeout=1500ms\n',
                       b'spring.jdbc.template.query-timeout=1500ms\n'*2,
                       b'spring.jdbc.template.query-timeout=1s\n',
                       b'customer.note=continued\\\nspring.jdbc.template.query-timeout=1500ms\n',
                       b'spring.jdbc.template.query-timeout=1500ms\nspring.jdbc.template.query\\u002dtimeout=9s\n',
                       b' spring.jdbc.template.query-timeout=1500ms\n',
                       b'spring.jdbc.template.query-timeout=1500ms\r\n',
                       b'spring.jdbc.template.query-timeout=1500ms'):
            with self.subTest(before=before),self.assertRaises(ValueError): upgrade.rewrite_query_property(before)

if __name__=='__main__': unittest.main(verbosity=2)
