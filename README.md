#### 1. **Réception de la Demande d'Approbation**

- Endpoint `/api/approvals/register` reçoit les demandes.
    
- Un OTP est généré pour l'approbateur via Twilio.
    
- Les détails de l’OTP sont stockés dans la table `ApprovalOTP`.
![example json approval request](https://github.com/user-attachments/assets/94474f51-56e5-4840-b83b-88fa42c7807b)

#### 2. **Envoi de l'OTP via Twilio**

- L'OTP est envoyé via Twilio avec `sendVerificationCode` dans `TwilioService`.
    
- Le `verificationSid` est récupéré pour suivre l’OTP.

#### 3. **Vérification de l'OTP par l'Approbateur**

-  **Valide** → L'OTP est confirmé, et la demande est envoyée pour traitement.
    
- **Invalide** → Après 3 erreurs, l’OTP est refusé et l’approbateur est informé.
    
- **Expiré** → Un message WhatsApp notifie l’approbateur qu’un nouveau code est requis.

![demonstration](https://github.com/user-attachments/assets/450b69e6-2dcf-4231-a1b8-de04c3108937)


#### 4. **Renvoi de l'OTP (Cas du Bouton)**

- L'implémentation du bouton "Resend OTP" 

- Le bouton "Resend OTP" permet à l'approbateur de redemander un code si l’OTP précédent a expiré ou échoué. Une fois le nouvel OTP envoyé via Twilio, l'ancien enregistrement est supprimé pour éviter les renvois en boucle.

#### 5. **Flux de Renvoi de l'OTP**

- **Finalisé** : Lorsqu'un approbateur traite un OTP expiré ou incorrect après plusieurs tentatives échouées, il peut cliquer sur le bouton "Resend OTP" dans le message WhatsApp.
- Un enregistrement dans `OtpResendMapping` est créé dès l’envoi initial de l’OTP, associant le code à la demande et à l’approbateur.

![cas de renvoie otp](https://github.com/user-attachments/assets/bd2c05a5-56a4-4e19-afe2-ce3b31a82898)



#### **6. Session approbateur**

* **Finalisé :** Afin de limiter les OTPs pour diminuer le couts et la confusion des approbateurs afin de ne pas se perdre entre les demandes otp et les demandes d'approbation, une session de 4h s'instancie et donne un accès a l'approbateur au demande directement sans avoir a entrer un code pour chaque demande.

#### **7. Traitement de la demande d'approbation par l'approbateur**

* **Finalisé :** Après validation de la demande ou alors après avoir été instancier dans la session, l'approbateur accède a la demande en affichant Origin de la demande "AccountingSystem", le nom de la personne ayant initier la demande "Mehdi", le type et ID de la demande "Invoice", "INV-444-001" et les boutons afin que l'approbateur puisse traité la demande en un seul click
#### **8. Rédaction d'un commentaire en cas de rejet ou mise en attente**

* **Finalisé** : Lorsqu'un approbateur met une demande d'approbation en attente ou alors en rejet, un commentaire lui est demande afin de justifier la décision, et stocker dans l'entité `Approval Request`pour avoir une trace et renvoyer le webhook au système extérieur via URL de Rappel fournit dans la demande d'approbation.

![commentaire en cas de rejet ou attente](https://github.com/user-attachments/assets/7b270d59-6070-4816-819e-7b325063de7a)


#### **9. Notification de Rappel**

* **Finalisé :** Configuration pour l'envoie d'une notification de rappel apres un temps déterminer si l'approbateur a négliger ou oublier de traiter une demande. Un seul message qui regroupe combien de demande n'ont pas été traité avec ID de chaque demande pour que l'approbateur puisse savoir de quelle demande s'agit il. Voici un exemple de test de notification après 1 mi

![notification de rappel test apres 1 minute ](https://github.com/user-attachments/assets/41eee3f0-0cfd-4066-8b07-179968c939dd)


Le **diagramme de flux** illustre un processus de validation d'une demande d'approbation.

![flowchart demande approbation whatsapp](https://github.com/user-attachments/assets/03701324-670a-4a19-bc79-84c9e2a502af)


