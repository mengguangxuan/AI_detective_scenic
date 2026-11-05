"""Offline tests for the paid-generation reservation boundary; no provider calls."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('scene_probe', Path(__file__).with_name('probe_tokenhub_scene.py'))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class SceneProbeBudgetTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.cache = Path(self.directory.name)
        self.cache_patch = patch.object(probe, 'CACHE', self.cache)
        self.cache_patch.start()
        self.addCleanup(self.cache_patch.stop)
        self.prompt = self.cache / 'prompt.txt'
        self.prompt.write_text('A bedroom', encoding='utf-8')

    def run_action(self, action, *extra):
        with patch.object(sys, 'argv', ['probe', action, '--round-id', 'one-turn', *extra]), patch('builtins.print'):
            probe.main()

    def test_ambiguous_failure_consumes_round_before_network_and_survives_retry(self):
        state = self.cache / 'one-turn/submission.json'

        def timeout(*args, **kwargs):
            self.assertEqual(json.loads(state.read_text())['generationAttempts'], 1)
            raise TimeoutError('offline simulated timeout')

        with patch.object(probe, 'request', side_effect=timeout) as network:
            with self.assertRaises(TimeoutError):
                self.run_action('submit', '--prompt', str(self.prompt))
            with self.assertRaises(FileExistsError):
                self.run_action('submit', '--prompt', str(self.prompt))
            self.assertEqual(network.call_count, 1)
        self.assertEqual(json.loads(state.read_text())['status'], 'submission_failed_or_unknown')

    def test_successful_submission_cannot_be_submitted_again(self):
        with patch.object(probe, 'request', return_value={'id': 'saved-task'}) as network:
            self.run_action('submit', '--prompt', str(self.prompt))
            with self.assertRaises(FileExistsError):
                self.run_action('submit', '--prompt', str(self.prompt))
            self.assertEqual(network.call_count, 1)

    def test_poll_queries_saved_task_without_post_payload(self):
        folder = self.cache / 'one-turn'
        folder.mkdir()
        (folder / 'submission.json').write_text(json.dumps({'taskId': 'saved-task'}))
        with patch.object(probe, 'request', return_value={'status': 'running', 'data': []}) as network:
            self.run_action('poll')
            network.assert_called_once_with('/wand/hunyuan-video/tasks/saved-task')

    def test_unknown_submission_is_not_recreated_by_poll(self):
        folder = self.cache / 'one-turn'
        folder.mkdir()
        (folder / 'submission.json').write_text('{}')
        with patch.object(probe, 'request') as network:
            with self.assertRaises(ValueError):
                self.run_action('poll')
            network.assert_not_called()


if __name__ == '__main__':
    unittest.main()
