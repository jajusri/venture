import { Router } from 'express';

export const deviceRouter = Router();

deviceRouter.post('/device/pair', (req, res) => {
  const { deviceId, pairingCode } = req.body ?? {};
  if (!deviceId || !pairingCode) {
    res.status(400).json({
      code: 'INVALID_PAIRING_REQUEST',
      message: 'deviceId and pairingCode are required.',
    });
    return;
  }

  // Pairing handshake stub — real approval flow arrives in Milestone 1.
  res.status(501).json({
    code: 'NOT_IMPLEMENTED',
    message: 'Device pairing is not implemented in Milestone 0.',
  });
});
